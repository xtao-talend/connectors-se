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
package org.talend.components.azure.eventhubs.source.streaming;

import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_CHARSET;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
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
import javax.json.JsonBuilderFactory;
import javax.json.JsonReaderFactory;
import javax.json.bind.Jsonb;
import javax.json.spi.JsonProvider;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DecoderFactory;
import org.talend.components.azure.eventhubs.runtime.converters.AvroConverter;
import org.talend.components.azure.eventhubs.runtime.converters.CSVConverter;
import org.talend.components.azure.eventhubs.runtime.converters.JsonConverter;
import org.talend.components.azure.eventhubs.runtime.converters.RecordConverter;
import org.talend.components.azure.eventhubs.runtime.converters.TextConverter;
import org.talend.components.azure.eventhubs.service.Messages;
import org.talend.components.azure.eventhubs.source.AzureEventHubsSource;
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

    private final RecordBuilderFactory recordBuilderFactory;

    private ReceiverManager receiverManager;

    private ScheduledExecutorService executorService;

    private Iterator<EventData> receivedEvents;

    private EventHubClient ehClient;

    private long count;

    private Messages messages;

    String[] partitionIds;

    private RecordConverter recordConverter;

    private JsonBuilderFactory jsonBuilderFactory;

    private JsonProvider jsonProvider;

    private JsonReaderFactory readerFactory;

    private Jsonb jsonb;

    private transient Schema schema;

    private transient GenericDatumReader<GenericRecord> datumReader;

    private transient BinaryDecoder decoder;

    public AzureEventHubsSamplingSource(@Option("configuration") final AzureEventHubsStreamInputConfiguration configuration,
            RecordBuilderFactory recordBuilderFactory, JsonBuilderFactory jsonBuilderFactory, JsonProvider jsonProvider,
            JsonReaderFactory readerFactory, Jsonb jsonb, Messages messages) {
        this.configuration = configuration;
        this.recordBuilderFactory = recordBuilderFactory;
        this.jsonBuilderFactory = jsonBuilderFactory;
        this.jsonProvider = jsonProvider;
        this.readerFactory = readerFactory;
        this.jsonb = jsonb;
        this.messages = messages;

    }

    @PostConstruct
    public void init() {
        try {
            executorService = Executors.newScheduledThreadPool(8);

            final ConnectionStringBuilder connStr = new ConnectionStringBuilder();
            if (configuration.getDataset().getConnection().isSpecifyEndpoint()) {
                connStr.setEndpoint(new URI(configuration.getDataset().getConnection().getEndpoint()));//
            } else {
                connStr.setNamespaceName(configuration.getDataset().getConnection().getNamespace());
            }
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
        try {
            if (receivedEvents == null || !receivedEvents.hasNext()) {
                log.debug("fetch messages...");
                // TODO let it configurable?
                Iterable<EventData> iterable = receiverManager.getBatchEventData(100);
                if (iterable == null) {
                    log.debug("no record available now!");
                    return null;
                }
                receivedEvents = iterable.iterator();
            }
            if (receivedEvents.hasNext()) {
                EventData eventData = receivedEvents.next();
                Record record = null;
                if (eventData != null) {
                    count++;
                    switch (configuration.getDataset().getValueFormat()) {
                    case AVRO: {
                        if (recordConverter == null) {
                            recordConverter = AvroConverter.of(recordBuilderFactory);
                        }
                        if (schema == null) {
                            schema = new org.apache.avro.Schema.Parser().parse(configuration.getDataset().getAvroSchema());
                            datumReader = new GenericDatumReader<GenericRecord>(schema);
                        }
                        decoder = DecoderFactory.get().binaryDecoder(eventData.getBytes(), decoder);
                        record = recordConverter.toRecord(datumReader.read(null, decoder));
                        break;
                    }
                    case CSV: {
                        if (recordConverter == null) {
                            recordConverter = CSVConverter.of(recordBuilderFactory,
                                    configuration.getDataset().getFieldDelimiter(), messages);
                        }
                        record = recordConverter.toRecord(new String(eventData.getBytes(), DEFAULT_CHARSET));
                        break;
                    }
                    case TEXT: {
                        if (recordConverter == null) {
                            recordConverter = TextConverter.of(recordBuilderFactory, messages);
                        }
                        record = recordConverter.toRecord(new String(eventData.getBytes(), DEFAULT_CHARSET));
                        break;
                    }
                    case JSON: {
                        if (recordConverter == null) {
                            recordConverter = JsonConverter.of(recordBuilderFactory, jsonBuilderFactory, jsonProvider,
                                    readerFactory, jsonb, messages);
                        }
                        record = recordConverter.toRecord(new String(eventData.getBytes(), DEFAULT_CHARSET));
                        break;
                    }
                    default:
                        throw new RuntimeException("To be implemented: " + configuration.getDataset().getValueFormat());
                    }
                    log.debug(record.toString());
                }
                return record;
            } else {
                return next();
            }
        } catch (Throwable e) {
            throw new IllegalStateException(e.getMessage(), e);
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
                        this.activedReceiver.setReceiveTimeout(Duration.ofMillis(1000));// TODO
                                                                                        // changeme
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