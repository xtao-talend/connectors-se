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
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_DNS;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_ENDPOINTS_PROTOCOL_NAME;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.ENDPOINT_SUFFIX_NAME;

import java.io.Serializable;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.talend.components.azure.eventhubs.service.UiActionService;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Producer;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import com.microsoft.azure.eventhubs.ConnectionStringBuilder;
import com.microsoft.azure.eventhubs.EventData;
import com.microsoft.azure.eventhubs.PartitionReceiver;
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

    private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

    private static final String PAYLOAD_COLUMN = "payload";

    private static Queue<EventData> receivedEvents = new LinkedList<EventData>();

    private final AzureEventHubsStreamInputConfiguration configuration;

    private final UiActionService service;

    private final RecordBuilderFactory builderFactory;

    private PartitionReceiver receiver;

    private ScheduledExecutorService executorService;

    private CompletableFuture<Void> processor;

    private EventProcessorHost host;

    public AzureEventHubsUnboundedSource(@Option("configuration") final AzureEventHubsStreamInputConfiguration configuration,
            final UiActionService service, final RecordBuilderFactory builderFactory) {
        this.configuration = configuration;
        this.service = service;
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
                    .setEndpoint(new URI(configuration.getDataset().getDatastore().getEndpoint()));
            eventHubConnectionString.setSasKeyName(configuration.getDataset().getDatastore().getSasKeyName());
            eventHubConnectionString.setSasKey(configuration.getDataset().getDatastore().getSasKey());
            eventHubConnectionString.setEventHubName(configuration.getDataset().getEventHubName());

            EventProcessorOptions options = new EventProcessorOptions();
            options.setMaxBatchSize(100);
            options.setExceptionNotification(new ErrorNotificationHandler());
            host = new EventProcessorHost(EventProcessorHost.createHostName(hostNamePrefix),
                    configuration.getDataset().getEventHubName(), configuration.getConsumerGroupName(),
                    eventHubConnectionString.toString(), storageConnectionString, configuration.getContainerName(),
                    executorService);
            processor = host.registerEventProcessor(EventProcessor.class, options);

            log.info("Registering host named " + host.getHostName());

        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }

    }

    @Producer
    public Record next() {
        if (!receivedEvents.isEmpty()) {
            EventData eventData = receivedEvents.poll();
            if (eventData != null) {
                Record.Builder recordBuilder = builderFactory.newRecordBuilder();
                recordBuilder.withString(PAYLOAD_COLUMN, new String(eventData.getBytes(), DEFAULT_CHARSET));
                // TODO remove this later
                log.info(eventData.getSystemProperties().getSequenceNumber() + " --> "
                        + new String(eventData.getBytes(), DEFAULT_CHARSET));
                return recordBuilder.build();
            }
        }
        return null;
    }

    @PreDestroy
    public void release() {
        try {
            processor.thenCompose((unused) -> {
                // This stage will only execute if registerEventProcessor succeeded.
                //
                // Processing of events continues until unregisterEventProcessor is called. Unregistering shuts down the
                // receivers on all currently owned leases, shuts down the instances of the event processor class, and
                // releases the leases for other instances of EventProcessorHost to claim.
                log.info("Unregistering host named " + host.getHostName());
                return host.unregisterEventProcessor();
            }).exceptionally((e) -> {
                log.error("Failure while unregistering: " + e.toString());
                return null;
            }).get(); // Wait for everything to finish before exiting main!
            executorService.shutdown();
        } catch (Exception e) {
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
            System.out.println("Host " + t.getHostname() + " received general error notification during " + t.getAction() + ": "
                    + t.getException().toString());
        }
    }

    public static class EventProcessor implements IEventProcessor {

        private int checkpointBatchingCount = 0;

        private EventData eventData;

        private boolean needUpdateCheckpoint;

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
            if (needUpdateCheckpoint && eventData != null) {
                context.checkpoint(eventData).get();
            }
            log.debug("Partition " + context.getPartitionId() + " is closing for reason " + reason.toString());
        }

        // onError is called when an error occurs in EventProcessorHost code that is tied to this partition, such as a
        // receiver
        // failure.
        // It is NOT called for exceptions thrown out of onOpen/onClose/onEvents. EventProcessorHost is responsible for
        // recovering
        // from
        // the error, if possible, or shutting the event processor down if not, in which case there will be a call to
        // onClose. The
        // notification provided to onError is primarily informational.
        @Override
        public void onError(PartitionContext context, Throwable error) {
            // make sure checkpoint update when not reach the batch
            if (needUpdateCheckpoint && eventData != null) {
                try {
                    context.checkpoint(eventData).get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
            log.error("Partition " + context.getPartitionId() + " onError: " + error.toString());
        }

        /**
         *
         * onEvents is called when events are received on this partition of the Event Hub. The maximum number of events in a batch
         * can be controlled via EventProcessorOptions. Also,
         * if the "invoke processor after receive timeout" option is set to true,
         * this method will be called with null when a receive timeout occurs.
         */

        @Override
        public void onEvents(PartitionContext context, Iterable<EventData> events) throws Exception {
            log.debug("Partition " + context.getPartitionId() + " got event batch");
            int eventCount = 0;
            for (EventData data : events) {
                receivedEvents.add(data);
                eventData = data;
                needUpdateCheckpoint = true;
                // It is important to have a try-catch around the processing of each event. Throwing out of onEvents
                // deprives
                // you of the chance to process any remaining events in the batch.
                try {
                    eventCount++;

                    // Checkpointing persists the current position in the event streaming for this partition and means
                    // that the next
                    // time any host opens an event processor on this event hub+consumer group+partition combination, it
                    // will start receiving at the event after this one. Checkpointing is usually not a fast operation, so there
                    // is
                    // a tradeoff between checkpointing frequently (to minimize the number of events that will be reprocessed
                    // after
                    // a crash, or if the partition lease is stolen) and checkpointing infrequently (to reduce the impact on event
                    // processing performance). Checkpointing every five events is an arbitrary choice for this sample.
                    this.checkpointBatchingCount++;
                    if ((checkpointBatchingCount % 5) == 0) {
                        log.debug("Updating Partition " + context.getPartitionId() + " checkpointing at "
                                + data.getSystemProperties().getOffset() + "," + data.getSystemProperties().getSequenceNumber());
                        // Checkpoints are created asynchronously. It is important to wait for the result of checkpointing
                        // before exiting onEvents or before creating the next checkpoint, to detect errors and to
                        // ensure proper ordering.
                        context.checkpoint(data).get();
                        needUpdateCheckpoint = false;
                    }
                } catch (Exception e) {
                    log.error("Processing failed for an event: " + e.toString());
                }
            }
            log.debug("Partition " + context.getPartitionId() + " batch size was " + eventCount + " for host "
                    + context.getOwner());
        }
    }
}