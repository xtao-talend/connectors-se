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
 *
 */

package org.talend.components.azure.eventhubs.source.streaming;

import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.ACCOUNT_KEY_NAME;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.ACCOUNT_NAME_NAME;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_CHARSET;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_DNS;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_ENDPOINTS_PROTOCOL_NAME;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.ENDPOINT_SUFFIX_NAME;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.PARTITION_ID;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.PAYLOAD_COLUMN;

import java.io.Serializable;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Producer;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import com.google.common.collect.EvictingQueue;
import com.microsoft.azure.eventhubs.ConnectionStringBuilder;
import com.microsoft.azure.eventhubs.EventData;
import com.microsoft.azure.eventprocessorhost.CloseReason;
import com.microsoft.azure.eventprocessorhost.EventProcessorHost;
import com.microsoft.azure.eventprocessorhost.EventProcessorOptions;
import com.microsoft.azure.eventprocessorhost.ExceptionReceivedEventArgs;
import com.microsoft.azure.eventprocessorhost.IEventProcessor;
import com.microsoft.azure.eventprocessorhost.PartitionContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Documentation("Source to consume eventhubs messages")
public class AzureEventHubsUnboundedSource implements Serializable {

    private static Queue<EventData> receivedEvents = new LinkedList<EventData>();

    private final AzureEventHubsStreamInputConfiguration configuration;

    private final RecordBuilderFactory builderFactory;

    private ScheduledExecutorService executorService;

    private CompletableFuture<Void> processor;

    private EventProcessorHost host;

    private static int commitEvery;

    private static volatile boolean processOpened;

    private static Map<String, Queue<EventData>> lastEventDataMap = new HashMap<>();

    public AzureEventHubsUnboundedSource(@Option("configuration") final AzureEventHubsStreamInputConfiguration configuration,
            final RecordBuilderFactory builderFactory) {
        this.configuration = configuration;
        this.builderFactory = builderFactory;
    }

    @PostConstruct
    public void init() {
        try {
            final String hostNamePrefix = "talend";
            executorService = Executors.newScheduledThreadPool(1);
            final String storageConnectionString = String.format("%s=%s;%s=%s;%s=%s;%s=%s", DEFAULT_ENDPOINTS_PROTOCOL_NAME,
                    configuration.getStorageConn().getProtocol(), ACCOUNT_NAME_NAME,
                    configuration.getStorageConn().getAccountName(), ACCOUNT_KEY_NAME,
                    configuration.getStorageConn().getAccountKey(), ENDPOINT_SUFFIX_NAME, DEFAULT_DNS);
            final ConnectionStringBuilder eventHubConnectionString = new ConnectionStringBuilder()//
                    .setEndpoint(new URI(configuration.getDataset().getConnection().getEndpoint()));
            eventHubConnectionString.setSasKeyName(configuration.getDataset().getConnection().getSasKeyName());
            eventHubConnectionString.setSasKey(configuration.getDataset().getConnection().getSasKey());
            eventHubConnectionString.setEventHubName(configuration.getDataset().getEventHubName());

            EventProcessorOptions options = new EventProcessorOptions();
            options.setExceptionNotification(new ErrorNotificationHandler());
            host = new EventProcessorHost(EventProcessorHost.createHostName(hostNamePrefix),
                    configuration.getDataset().getEventHubName(), configuration.getConsumerGroupName(),
                    eventHubConnectionString.toString(), storageConnectionString, configuration.getContainerName(),
                    executorService);
            processor = host.registerEventProcessor(EventProcessor.class, options);

            log.info("Registering host named " + host.getHostName());

            commitEvery = configuration.getCommitOffsetEvery();
            processOpened = true;

        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }

    }

    @Producer
    public Record next() {
        EventData eventData = receivedEvents.poll();
        if (eventData != null) {
            String partitionKey = String.valueOf(eventData.getProperties().get(PARTITION_ID));
            if (!lastEventDataMap.containsKey(partitionKey)) {
                Queue<EventData> lastEvent = EvictingQueue.create(1);
                lastEvent.add(eventData);
                lastEventDataMap.put(partitionKey, lastEvent);
            } else {
                lastEventDataMap.get(partitionKey).add(eventData);
            }
            Record.Builder recordBuilder = builderFactory.newRecordBuilder();
            recordBuilder.withString(PAYLOAD_COLUMN, new String(eventData.getBytes(), DEFAULT_CHARSET));
            log.info(partitionKey + "-" + eventData.getSystemProperties().getSequenceNumber() + " --> "
                    + new String(eventData.getBytes(), DEFAULT_CHARSET));
            return recordBuilder.build();
        }
        return null;
    }

    @PreDestroy
    public void release() {
        try {
            processOpened = false;
            log.info("closing...");
            processor.thenCompose((unused) -> {
                // This stage will only execute if registerEventProcessor succeeded.
                //
                // Processing of events continues until unregisterEventProcessor is called. Unregistering shuts down the
                // receivers on all currently owned leases, shuts down the instances of the event processor class, and
                // releases the leases for other instances of EventProcessorHost to claim.
                log.info("Unregistering host named " + host.getHostName());
                return host.unregisterEventProcessor();
            }).get(); // Wait for everything to finish before exiting main!
        } catch (Exception e) {
            log.error("Failure while unregistering: " + e.getMessage());
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    // The general notification handler is an object that derives from Consumer<> and takes an
    // ExceptionReceivedEventArgs object
    // as an argument. The argument provides the details of the error: the exception that occurred and the action (what
    // EventProcessorHost
    // was doing) during which the error occurred. The complete list of actions can be found in
    // EventProcessorHostActionStrings.
    public static class ErrorNotificationHandler implements Consumer<ExceptionReceivedEventArgs> {

        @Override
        public void accept(ExceptionReceivedEventArgs t) {
            log.warn("Host " + t.getHostname() + " received general error notification during " + t.getAction() + ": "
                    + t.getException().toString());
        }
    }

    public static class EventProcessor implements IEventProcessor {

        private int checkpointBatchingCount = 0;

        // OnOpen is called when a new event processor instance is created by the host. In a real implementation, this
        // is the place to do initialization so that events can be processed when they arrive, such as opening a
        // database
        // connection.
        @Override
        public void onOpen(PartitionContext context) throws Exception {
            log.debug("Partition " + context.getPartitionId() + " is opening");
        }

        // OnClose is called when an event processor instance is being shut down. The reason argument indicates whether
        // the shut
        // down
        // is because another host has stolen the lease for this partition or due to error or host shutdown. In a real
        // implementation,
        // this is the place to do cleanup for resources that were opened in onOpen.
        @Override
        public void onClose(PartitionContext context, CloseReason reason) throws Exception {
            // make sure checkpoint update when not reach the batch
            if (lastEventDataMap.containsKey(context.getPartitionId())) {
                EventData eventData = lastEventDataMap.get(context.getPartitionId()).poll();
                if (eventData != null) {
                    log.info("####################: " + receivedEvents.size());
                    receivedEvents.clear();
                    context.checkpoint(eventData).get();
                    log.debug("[onClose]onErrorUpdating Partition " + context.getPartitionId() + " checkpointing at "
                            + eventData.getSystemProperties().getOffset() + ","
                            + eventData.getSystemProperties().getSequenceNumber());
                }
            }
            log.info("Partition " + context.getPartitionId() + " is closing for reason " + reason.toString());
        }

        // onError is called when an error occurs in EventProcessorHost code that is tied to this partition, such as a receiver
        // to onClose. The notification provided to onError is primarily informational.
        @Override
        public void onError(PartitionContext context, Throwable error) {
            // make sure checkpoint update when not reach the batch
            if (lastEventDataMap.containsKey(context.getPartitionId())) {
                EventData eventData = lastEventDataMap.get(context.getPartitionId()).poll();
                if (eventData != null) {
                    try {
                        receivedEvents.clear();
                        context.checkpoint(eventData).get();
                        log.debug("[onError] Updating Partition " + context.getPartitionId() + " checkpointing at "
                                + eventData.getSystemProperties().getOffset() + ","
                                + eventData.getSystemProperties().getSequenceNumber());
                    } catch (Exception e) {
                        log.error("Partition " + context.getPartitionId() + " onError: " + e.getMessage());
                    }
                }
            }
            log.error("Partition " + context.getPartitionId() + " onError: " + error.toString());
        }

        /**
         * onEvents is called when events are received on this partition of the Event Hub.
         * The maximum number of events in a batch can be controlled via EventProcessorOptions. Also,
         * if the "invoke processor after receive timeout" option is set to true,
         * this method will be called with null when a receive timeout occurs.
         */

        @Override
        public void onEvents(PartitionContext context, Iterable<EventData> events) throws InterruptedException {
            log.debug("Partition " + context.getPartitionId() + " got event batch");
            int eventCount = 0;
            for (EventData data : events) {
                data.getProperties().put(PARTITION_ID, context.getPartitionId());
                if (!processOpened) {
                    // ignore the received event data, this would not handled by component
                    receivedEvents.clear();
                    return;
                } else {
                    receivedEvents.add(data);
                }
                // It is important to have a try-catch around the processing of each event. Throwing out of onEvents deprives you
                // of the chance to process any remaining events in the batch.
                try {
                    eventCount++;

                    // Checkpointing persists the current position in the event streaming for this partition and means that the
                    // next time any host opens an event processor on this event hub+consumer group+partition combination, it will
                    // start receiving at the event after this one. Checkpointing is usually not a fast operation, so there is a
                    // tradeoff between checkpointing frequently (to minimize the number of events that will be reprocessed after
                    // a crash, or if the partition lease is stolen) and checkpointing infrequently (to reduce the impact on event
                    // processing performance). Checkpointing every five events is an arbitrary choice for this sample.
                    this.checkpointBatchingCount++;
                    if ((checkpointBatchingCount % commitEvery) == 0) {
                        log.debug("[onEvents]Updating Partition " + context.getPartitionId() + " checkpointing at "
                                + data.getSystemProperties().getOffset() + "," + data.getSystemProperties().getSequenceNumber());
                        // Checkpoints are created asynchronously. It is important to wait for the result of checkpointing
                        // before exiting onEvents or before creating the next checkpoint, to detect errors and to
                        // ensure proper ordering.
                        context.checkpoint(data).get();
                    }
                } catch (Exception e) {
                    log.error("Processing failed for an event: " + e.toString());
                }
                log.debug("Partition " + context.getPartitionId() + " batch size was " + eventCount + " for host "
                        + context.getOwner());
            }
        }
    }
}