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

import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.ACCOUNT_KEY_NAME;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.ACCOUNT_NAME_NAME;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_CHARSET;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_DNS;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_ENDPOINTS_PROTOCOL_NAME;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.ENDPOINT_SUFFIX_NAME;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.PARTITION_ID;

import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

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
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import com.google.common.collect.EvictingQueue;
import com.microsoft.azure.eventhubs.ConnectionStringBuilder;
import com.microsoft.azure.eventhubs.EventData;
import com.microsoft.azure.eventhubs.EventPosition;
import com.microsoft.azure.eventprocessorhost.CloseReason;
import com.microsoft.azure.eventprocessorhost.EventProcessorHost;
import com.microsoft.azure.eventprocessorhost.EventProcessorOptions;
import com.microsoft.azure.eventprocessorhost.ExceptionReceivedEventArgs;
import com.microsoft.azure.eventprocessorhost.IEventProcessor;
import com.microsoft.azure.eventprocessorhost.PartitionContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Documentation("Source to consume eventhubs messages")
public class AzureEventHubsUnboundedSource implements Serializable, AzureEventHubsSource {

    private static Queue<EventData> receivedEvents = new LinkedList<EventData>();

    private final AzureEventHubsStreamInputConfiguration configuration;

    private final Messages messages;

    private ScheduledExecutorService executorService;

    private CompletableFuture<Void> processor;

    private EventProcessorHost host;

    private RecordConverter recordConverter;

    private transient Schema schema;

    private transient GenericDatumReader<GenericRecord> datumReader;

    private transient BinaryDecoder decoder;

    private RecordBuilderFactory recordBuilderFactory;

    private JsonBuilderFactory jsonBuilderFactory;

    private JsonProvider jsonProvider;

    private JsonReaderFactory readerFactory;

    private Jsonb jsonb;

    private static int commitEvery;

    private static volatile boolean processOpened;

    private static Map<String, Queue<EventData>> lastEventDataMap = new HashMap<>();

    public AzureEventHubsUnboundedSource(@Option("configuration") final AzureEventHubsStreamInputConfiguration configuration,
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
            options.setInitialPositionProvider(getPosition());
            host = new EventProcessorHost(EventProcessorHost.createHostName(hostNamePrefix),
                    configuration.getDataset().getEventHubName(), configuration.getConsumerGroupName(),
                    eventHubConnectionString.toString(), storageConnectionString, configuration.getContainerName(),
                    executorService);
            processor = host.registerEventProcessor(EventProcessor.class, options);

            log.debug("Registering host named " + host.getHostName());

            commitEvery = configuration.getCommitOffsetEvery();
            processOpened = true;

        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }

    }

    @Producer
    public Record next() {
        EventData eventData = receivedEvents.poll();
        Record record = null;
        if (eventData != null) {
            try {
                String partitionKey = String.valueOf(eventData.getProperties().get(PARTITION_ID));
                if (!lastEventDataMap.containsKey(partitionKey)) {
                    Queue<EventData> lastEvent = EvictingQueue.create(1);
                    lastEvent.add(eventData);
                    lastEventDataMap.put(partitionKey, lastEvent);
                } else {
                    lastEventDataMap.get(partitionKey).add(eventData);
                }
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
                    GenericRecord genericRecord = datumReader.read(null, decoder);
                    record = recordConverter.toRecord(genericRecord);
                    break;
                }
                case CSV: {
                    if (recordConverter == null) {
                        recordConverter = CSVConverter.of(recordBuilderFactory, configuration.getDataset().getFieldDelimiter(),
                                messages);
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
                        recordConverter = JsonConverter.of(recordBuilderFactory, jsonBuilderFactory, jsonProvider, readerFactory,
                                jsonb, messages);
                    }
                    record = recordConverter.toRecord(new String(eventData.getBytes(), DEFAULT_CHARSET));
                    break;
                }
                default:
                    throw new RuntimeException("To be implemented: " + configuration.getDataset().getValueFormat());
                }
                log.debug(record.toString());
                return record;
            } catch (Throwable e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
        return null;
    }

    @PreDestroy
    public void release() {
        try {
            processOpened = false;
            log.debug("closing...");
            processor.thenCompose((unused) -> {
                // This stage will only execute if registerEventProcessor succeeded.
                //
                // Processing of events continues until unregisterEventProcessor is called. Unregistering shuts down the
                // receivers on all currently owned leases, shuts down the instances of the event processor class, and
                // releases the leases for other instances of EventProcessorHost to claim.
                log.debug("Unregistering host named " + host.getHostName());
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
                    receivedEvents.clear();
                    context.checkpoint(eventData).get();
                    log.debug("[onClose]onErrorUpdating Partition " + context.getPartitionId() + " checkpointing at "
                            + eventData.getSystemProperties().getOffset() + ","
                            + eventData.getSystemProperties().getSequenceNumber());
                }
            }
            log.debug("Partition " + context.getPartitionId() + " is closing for reason " + reason.toString());
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

    private Function<String, EventPosition> getPosition() {
        final EventPosition position;
        if (AzureEventHubsStreamInputConfiguration.OffsetResetStrategy.EARLIEST.equals(configuration.getAutoOffsetReset())) {
            position = EventPosition.fromStartOfStream();
        } else if (AzureEventHubsStreamInputConfiguration.OffsetResetStrategy.LATEST.equals(configuration.getAutoOffsetReset())) {
            position = EventPosition.fromEndOfStream();
        } else if (AzureEventHubsStreamInputConfiguration.OffsetResetStrategy.SEQUENCE
                .equals(configuration.getAutoOffsetReset())) {
            // every partition seq maybe not same
            throw new IllegalArgumentException("seems useless for this!");
        } else if (AzureEventHubsStreamInputConfiguration.OffsetResetStrategy.DATETIME
                .equals(configuration.getAutoOffsetReset())) {
            Instant enqueuedDateTime = null;
            if (configuration.getEnqueuedDateTime() == null) {
                // default query from now
                enqueuedDateTime = Instant.now();
            } else {
                enqueuedDateTime = Instant.parse(configuration.getEnqueuedDateTime());
            }
            position = EventPosition.fromEnqueuedTime(enqueuedDateTime);
        } else {
            throw new IllegalArgumentException("unsupported strategy!!" + configuration.getAutoOffsetReset());
        }
        return new Function<String, EventPosition>() {

            public EventPosition apply(String t) {
                return position;
            }
        };
    }
}