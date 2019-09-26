/*
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.talend.components.azure.eventhubs.source.batch;

import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_CHARSET;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.PAYLOAD_COLUMN;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.talend.components.azure.eventhubs.service.Messages;
import org.talend.components.azure.eventhubs.service.UiActionService;
import org.talend.components.azure.eventhubs.source.AzureEventHubsSource;
import org.talend.components.azure.eventhubs.source.streaming.AzureEventHubsStreamInputConfiguration;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Producer;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import com.microsoft.azure.eventhubs.ConnectionStringBuilder;
import com.microsoft.azure.eventhubs.EventData;
import com.microsoft.azure.eventhubs.EventHubClient;
import com.microsoft.azure.eventhubs.EventHubException;
import com.microsoft.azure.eventhubs.EventHubRuntimeInformation;
import com.microsoft.azure.eventhubs.EventPosition;
import com.microsoft.azure.eventhubs.PartitionReceiver;
import com.microsoft.azure.eventhubs.PartitionRuntimeInformation;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Documentation("Source to consume eventhubs messages")
public class AzureEventHubsSamplingSource implements Serializable, AzureEventHubsSource {

    private final AzureEventHubsStreamInputConfiguration configuration;

    private final RecordBuilderFactory builderFactory;

    private ReceiverManager receiverManager;

    private ScheduledExecutorService executorService;

    private Iterator<EventData> receivedEvents;

    private EventHubClient ehClient;

    private long count;

    private Messages messages;

    String[] partitionIds;

    public AzureEventHubsSamplingSource(@Option("configuration") final AzureEventHubsStreamInputConfiguration configuration,
             final RecordBuilderFactory builderFactory, final Messages messages) {
        this.configuration = configuration;
        this.builderFactory = builderFactory;
        this.messages = messages;
    }

    @PostConstruct
    public void init() {
        try {
            executorService = Executors.newScheduledThreadPool(8);
            final ConnectionStringBuilder connStr = new ConnectionStringBuilder()//
                    .setEndpoint(new URI(configuration.getDataset().getConnection().getEndpoint()));
            connStr.setSasKeyName(configuration.getDataset().getConnection().getSasKeyName());
            connStr.setSasKey(configuration.getDataset().getConnection().getSasKey());
            connStr.setEventHubName(configuration.getDataset().getEventHubName());

            ehClient = EventHubClient.createSync(connStr.toString(), executorService);
            receiverManager = new ReceiverManager();
            EventHubRuntimeInformation runtimeInfo = ehClient.getRuntimeInformation().get();
            partitionIds = runtimeInfo.getPartitionIds();
            receiverManager.addPartitions(partitionIds);
            if (!receiverManager.isReceiverAvailable()) {
                throw new IllegalStateException(messages.errorNoAvailableReceiver());
            }
        } catch (IOException | EventHubException | URISyntaxException | ExecutionException | InterruptedException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }

    }

    @Producer
    public Record next() {
        while (true) {
            try {
                if (receivedEvents == null || !receivedEvents.hasNext()) {
                    log.debug("fetch messages...");
                    // TODO let it configurable?
                    Iterable<EventData> iterable = receiverManager.getBatchEventData(100);
                    if (iterable == null) {
                        return null;
                    }
                    receivedEvents = iterable.iterator();
                }
                if (receivedEvents.hasNext()) {
                    EventData eventData = receivedEvents.next();
                    if (eventData != null) {
                        Record.Builder recordBuilder = builderFactory.newRecordBuilder();
                        recordBuilder.withString(PAYLOAD_COLUMN, new String(eventData.getBytes(), DEFAULT_CHARSET));
                        count++;
                        return recordBuilder.build();
                    }
                } else {
                    continue;
                }
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
    }

    @PreDestroy
    public void release() {
        try {
            receiverManager.closeAll();
            ehClient.closeSync();
            executorService.shutdown();
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    class ReceiverManager {

        private Map<String, EventPosition> eventPositionMap;

        private Queue<String> partitionInQueue;

        PartitionReceiver activedReceiver;

        ReceiverManager() {
            this.eventPositionMap = new LinkedHashMap<>();
            this.partitionInQueue = new LinkedList<>();
        }

        void addPartitions(String... partitionIds) throws ExecutionException, InterruptedException {
            for (String partitionId : partitionIds) {
                // This would check whether position config is validate or not at the moment
                if (!eventPositionMap.containsKey(partitionId)) {
                    PartitionRuntimeInformation partitionRuntimeInfo = ehClient.getPartitionRuntimeInformation(partitionId).get();
                    try {
                        EventPosition position = EventPosition.fromStartOfStream();
                        receiverManager.updatePartitionPositation(partitionId, position);
                    } catch (IllegalArgumentException e) {
                        log.warn(e.getMessage());
                    }
                }
                // add partition in queue wait to read
                if (!partitionInQueue.contains(partitionId)) {
                    partitionInQueue.add(partitionId);
                }
            }
        }

        boolean isReceiverAvailable() throws EventHubException {
            // eventPositionMap and partitionInQueue should not empty
            if (activedReceiver == null && !this.eventPositionMap.isEmpty()) {
                while (!partitionInQueue.isEmpty()) {
                    String partitionId = partitionInQueue.poll();
                    if (partitionId != null && eventPositionMap.get(partitionId) == null) {
                        // No available position to create receiver. continue check next
                        continue;
                    } else {
                        this.activedReceiver = ehClient.createEpochReceiverSync(configuration.getConsumerGroupName(), partitionId,
                                eventPositionMap.get(partitionId), Integer.MAX_VALUE);
                        this.activedReceiver.setReceiveTimeout(Duration.ofSeconds(20L));//TODO changeme
                        break;
                    }
                }
            }
            return activedReceiver != null;
        }

        void updatePartitionPositation(String partitionId, EventPosition position) {
            eventPositionMap.put(partitionId, position);
        }

        Iterable<EventData> getBatchEventData(int maxBatchSize) throws EventHubException {
            while (isReceiverAvailable()) {
                Iterable<EventData> iterable = activedReceiver.receiveSync(maxBatchSize);
                if (iterable == null) {
                    // Current receiver no data received at the moment
                    activedReceiver.closeSync();
                    activedReceiver = null;
                    continue;
                }
                // update the position which current partition have read
                updatePartitionPositation(activedReceiver.getPartitionId(), activedReceiver.getEventPosition());
                return iterable;
            }
            return null;
        }

        void closeAll() throws EventHubException {
            eventPositionMap.clear();
            partitionInQueue.clear();
            if (activedReceiver != null) {
                activedReceiver.closeSync();
                activedReceiver = null;
            }
        }

    }

}