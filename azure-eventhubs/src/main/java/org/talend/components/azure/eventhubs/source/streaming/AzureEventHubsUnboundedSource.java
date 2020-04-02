/*
 * Copyright (C) 2006-2020 Talend Inc. - www.talend.com
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

import static com.azure.messaging.eventhubs.implementation.ClientConstants.ENDPOINT_FORMAT;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_DOMAIN_NAME;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.EH_CONNECTION_PATTERN;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.PARTITION_ID;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.json.JsonBuilderFactory;
import javax.json.JsonReaderFactory;
import javax.json.bind.Jsonb;
import javax.json.spi.JsonProvider;

import org.apache.commons.lang3.StringUtils;
import org.talend.components.azure.datastore.AzureCloudConnection;
import org.talend.components.azure.eventhubs.runtime.adapter.ContentAdapterFactory;
import org.talend.components.azure.eventhubs.runtime.adapter.EventDataContentAdapter;
import org.talend.components.azure.eventhubs.service.Messages;
import org.talend.components.azure.eventhubs.service.UiActionService;
import org.talend.components.azure.eventhubs.source.AzureEventHubsSource;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Producer;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import com.azure.core.amqp.AmqpRetryMode;
import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventProcessorClient;
import com.azure.messaging.eventhubs.EventProcessorClientBuilder;
import com.azure.messaging.eventhubs.checkpointstore.blob.BlobCheckpointStore;
import com.azure.messaging.eventhubs.models.Checkpoint;
import com.azure.messaging.eventhubs.models.CloseContext;
import com.azure.messaging.eventhubs.models.ErrorContext;
import com.azure.messaging.eventhubs.models.EventContext;
import com.azure.messaging.eventhubs.models.EventPosition;
import com.azure.messaging.eventhubs.models.InitializationContext;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.google.common.collect.EvictingQueue;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

@Slf4j
@Documentation("Source to consume eventhubs messages")
public class AzureEventHubsUnboundedSource implements Serializable, AzureEventHubsSource {

    private final UiActionService service;

    private static Queue<EventData> receivedEvents = new LinkedList<EventData>();

    private final AzureEventHubsStreamInputConfiguration configuration;

    private final transient Messages messages;

    private transient EventProcessorClient eventProcessorClient;

    private final EventDataContentAdapter contentFormatter;

    private static Map<String, Queue<EventData>> lastEventDataMap = new HashMap<>();

    /**
     * Keeps track of the number of events processed from each partition.
     * Key: Partition id
     * Value: Number of events processed for each partition.
     */
    private final ConcurrentHashMap<String, Integer> eventsProcessed = new ConcurrentHashMap<>();

    private BlobCheckpointStore blobCheckpointStore;

    public static final String ENDPOINT_PATTERN = "sb://(.*)";

    public AzureEventHubsUnboundedSource(@Option("configuration") final AzureEventHubsStreamInputConfiguration configuration,
            final UiActionService service, RecordBuilderFactory recordBuilderFactory, JsonBuilderFactory jsonBuilderFactory,
            JsonProvider jsonProvider, JsonReaderFactory readerFactory, Jsonb jsonb, Messages messages) {
        this.configuration = configuration;
        this.service = service;
        this.contentFormatter = ContentAdapterFactory.getAdapter(configuration.getDataset(), recordBuilderFactory,
                jsonBuilderFactory, jsonProvider, readerFactory, jsonb, messages);
        this.messages = messages;

        // check whether all required properties are set
        this.validateConfiguration();

    }

    @PostConstruct
    public void init() {
        try {
            final String ownerId = "talend";
            // get formatted eventhubs connecting string
            String endpoint = null;
            if (configuration.getDataset().getConnection().isSpecifyEndpoint()) {
                endpoint = configuration.getDataset().getConnection().getEndpoint();//
            } else {
                endpoint = String.format(Locale.US, ENDPOINT_FORMAT, configuration.getDataset().getConnection().getNamespace(),
                        DEFAULT_DOMAIN_NAME);
            }
            String ehConnString = String.format(EH_CONNECTION_PATTERN, endpoint,
                    configuration.getDataset().getConnection().getSasKeyName(),
                    configuration.getDataset().getConnection().getSasKey(), configuration.getDataset().getEventHubName());

            AzureCloudConnection cloudConnection = new AzureCloudConnection();
            cloudConnection.setUseAzureSharedSignature(configuration.getCheckpointStore().isUseAzureSharedSignature());
            cloudConnection.setSignatureConnection(configuration.getCheckpointStore().getSignatureConnection());
            cloudConnection.setAccountConnection(configuration.getCheckpointStore().getAccountConnection());
            cloudConnection.setEndpointSuffix(configuration.getCheckpointStore().getEndpointSuffix());
            BlobContainerAsyncClient blobContainerAsyncClient = service.createBlobContainerAsyncClient(cloudConnection,
                    configuration.getContainerName());
            Matcher matcher = Pattern.compile(ENDPOINT_PATTERN).matcher(endpoint);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(messages.invalidatedSASURL());
            }
            String fullyQualifiedNamespace = matcher.group(1);
            // create the container if it not exist
            if (!blobContainerAsyncClient.exists().toFuture().get()) {
                blobContainerAsyncClient.create().toFuture().get();
            }
            blobCheckpointStore = new BlobCheckpointStore(blobContainerAsyncClient);
            // init checkpoint and partition ownership
            Flux<Checkpoint> checkpoints = blobCheckpointStore.listCheckpoints(fullyQualifiedNamespace,
                    configuration.getContainerName(), configuration.getConsumerGroupName());
            Map<String, EventPosition> eventPosition = new HashMap<>();
            // connection retry options when query partition ids, should not same with query event data retry option
            AmqpRetryOptions connRetryOptions = new AmqpRetryOptions() //
                    .setMaxDelay(Duration.ofSeconds(30)) //
                    .setDelay(Duration.ofMillis(500)) //
                    .setMaxRetries(5) //
                    .setTryTimeout(Duration.ofSeconds(30)) //
                    .setMode(AmqpRetryMode.EXPONENTIAL); //
            for (String partitionId : service.getPartitionIds(configuration.getDataset().getConnection(),
                    configuration.getDataset().getEventHubName(), connRetryOptions)) {
                eventPosition.put(partitionId, getPosition());
            }
            if (checkpoints != null && checkpoints.toIterable().iterator().hasNext()) {
                for (Checkpoint checkpoint : checkpoints.toIterable()) {
                    eventPosition.put(checkpoint.getPartitionId(),
                            EventPosition.fromSequenceNumber(checkpoint.getSequenceNumber()));
                }
            }

            // Set some custom retry options other than the default set.
            AmqpRetryOptions retryOptions = new AmqpRetryOptions().setDelay(Duration.ofMillis(500))//
                    .setMaxDelay(Duration.ofSeconds(30)) //
                    .setMaxRetries(Integer.MAX_VALUE - 1) // can't set Integer.MAX_VALUE directly
                    .setTryTimeout(Duration.ofSeconds(30)) //
                    .setMode(AmqpRetryMode.EXPONENTIAL); //

            final EventProcessorClientBuilder eventProcessorClientBuilder = new EventProcessorClientBuilder()
                    .connectionString(ehConnString) //
                    .consumerGroup(configuration.getConsumerGroupName()) //
                    .retry(retryOptions) //
                    .processPartitionInitialization(initializationContext -> onInitialize(initializationContext)) //
                    .processPartitionClose(closeContext -> onClose(closeContext)) //
                    .processEvent(eventContext -> onEvent(eventContext)).processError(errorContext -> onError(errorContext)) //
                    .initialPartitionEventPosition(eventPosition) //
                    .checkpointStore(blobCheckpointStore); //

            eventProcessorClient = eventProcessorClientBuilder.buildEventProcessorClient();
            // Starts the event processor
            eventProcessorClient.start();

        } catch (Throwable e) {
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
                record = contentFormatter.toRecord(eventData.getBody());
                return record;
            } catch (Throwable e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }
        return null;
    }

    @PreDestroy
    public void release() {
        // Stops the event processor
        if (eventProcessorClient != null) {
            eventProcessorClient.stop();
        }
    }

    /**
     * Get the start position for read, it would called when there is no checkpoint stored in target checkpoint store
     *
     * @return configured position
     */
    private EventPosition getPosition() {
        final EventPosition position;
        if (AzureEventHubsStreamInputConfiguration.OffsetResetStrategy.EARLIEST.equals(configuration.getAutoOffsetReset())) {
            position = EventPosition.earliest();
        } else if (AzureEventHubsStreamInputConfiguration.OffsetResetStrategy.LATEST.equals(configuration.getAutoOffsetReset())) {
            position = EventPosition.latest();
        } else if (AzureEventHubsStreamInputConfiguration.OffsetResetStrategy.SEQUENCE
                .equals(configuration.getAutoOffsetReset())) {
            position = EventPosition.fromSequenceNumber(configuration.getSequenceNum());
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
        return position;
    }

    /**
     * Check required configuration property
     */
    private void validateConfiguration() {
        if (StringUtils.isBlank(configuration.getConsumerGroupName())) {
            throw new IllegalArgumentException(messages.missingConsumerGroup());
        }
        if (StringUtils.isBlank(configuration.getContainerName())) {
            throw new IllegalArgumentException(messages.missingContainerName());
        }
        if (AzureEventHubsStreamInputConfiguration.OffsetResetStrategy.DATETIME.equals(configuration.getAutoOffsetReset())) {
            if (StringUtils.isBlank(configuration.getEnqueuedDateTime())) {
                throw new IllegalArgumentException(messages.missingEnqueuedDateTime());
            }
        }
        if (configuration.getCheckpointStore() == null) {
            throw new IllegalArgumentException(messages.missingCheckpointStore());
        }
    }

    /**
     * When an occurs, reports that error to a log.
     *
     * @param errorContext Context information for the partition in which this error occurred.
     */
    void onError(ErrorContext errorContext) {
        log.error("Error occurred in partition processor for partition {}, {}",
                errorContext.getPartitionContext().getPartitionId(), errorContext.getThrowable());
        // make sure checkpoint update when not reach the batch
        if (lastEventDataMap.containsKey(errorContext.getPartitionContext().getPartitionId())) {
            EventData eventData = lastEventDataMap.get(errorContext.getPartitionContext().getPartitionId()).poll();
            if (eventData != null) {
                try {
                    receivedEvents.clear();
                    if (blobCheckpointStore != null) {
                        Checkpoint checkpoint = new Checkpoint()
                                .setFullyQualifiedNamespace(errorContext.getPartitionContext().getFullyQualifiedNamespace())
                                .setEventHubName(errorContext.getPartitionContext().getEventHubName())
                                .setConsumerGroup(errorContext.getPartitionContext().getConsumerGroup())
                                .setPartitionId(errorContext.getPartitionContext().getPartitionId())
                                .setSequenceNumber(eventData.getSequenceNumber()).setOffset(eventData.getOffset());
                        try {
                            blobCheckpointStore.updateCheckpoint(checkpoint).toFuture().get();
                        } catch (InterruptedException | ExecutionException e) {
                            throw new IllegalArgumentException(e);
                        }
                    }
                } catch (Exception e) {
                    log.error("Partition " + errorContext.getPartitionContext().getPartitionId() + " onError: " + e.getMessage());
                }
            }
        }
        log.error("Error occurred processing partition '{}'. Exception: {}", errorContext.getPartitionContext().getPartitionId(),
                errorContext.getThrowable());
    }

    /**
     * On initialisation, keeps track of which partitions it is processing.
     *
     * @param initializationContext Information about partition it is processing.
     */
    void onInitialize(InitializationContext initializationContext) {
        String partitionId = initializationContext.getPartitionContext().getPartitionId();
        log.info("Starting to process partition {}", partitionId);
    }

    /**
     * Invoked when a partition is no longer being processed.
     *
     * @param closeContext Context information for the partition that is no longer being processed.
     */
    void onClose(CloseContext closeContext) {
        // make sure checkpoint update when not reach the batch
        if (lastEventDataMap.containsKey(closeContext.getPartitionContext().getPartitionId())) {
            EventData eventData = lastEventDataMap.get(closeContext.getPartitionContext().getPartitionId()).poll();
            if (eventData != null) {
                receivedEvents.clear();
                if (blobCheckpointStore != null) {
                    Checkpoint checkpoint = new Checkpoint()
                            .setFullyQualifiedNamespace(closeContext.getPartitionContext().getFullyQualifiedNamespace())
                            .setEventHubName(closeContext.getPartitionContext().getEventHubName())
                            .setConsumerGroup(closeContext.getPartitionContext().getConsumerGroup())
                            .setPartitionId(closeContext.getPartitionContext().getPartitionId())
                            .setSequenceNumber(eventData.getSequenceNumber()).setOffset(eventData.getOffset());
                    try {
                        blobCheckpointStore.updateCheckpoint(checkpoint).toFuture().get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new IllegalArgumentException(e);
                    }
                }
            }
        }
        log.info("Stopping processing of partition {}. Reason: {}", closeContext.getPartitionContext().getPartitionId(),
                closeContext.getCloseReason());
        eventsProcessed.remove(closeContext.getPartitionContext().getPartitionId());
    }

    /**
     * Processes an event from the partition. Aggregates the number of events that were processed in this partition.
     *
     * @param eventContext Information about which partition this event was in.
     */
    void onEvent(EventContext eventContext) {
        final Integer count = eventsProcessed.compute(eventContext.getPartitionContext().getPartitionId(),
                (key, value) -> value == null ? 1 : value + 1);

        log.debug("Processing event from partition " + eventContext.getPartitionContext().getPartitionId()
                + " with sequence number " + eventContext.getEventData().getSequenceNumber());
        EventData data = eventContext.getEventData();
        if (!eventProcessorClient.isRunning()) {
            // ignore the received event data, this would not handled by component
            receivedEvents.clear();
            return;
        } else {
            receivedEvents.add(data);
        }
        if ((eventsProcessed.get(eventContext.getPartitionContext().getPartitionId())
                % configuration.getCommitOffsetEvery()) == 0) {
            // Checkpoints are created asynchronously. It is important to wait for the result of checkpointing
            // before exiting onEvents or before creating the next checkpoint, to detect errors and to
            // ensure proper ordering.
            try {
                eventContext.updateCheckpointAsync().toFuture().get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IllegalArgumentException(e);
            }
        }
        log.info("Event {} received for partition: {}. # of events processed: {}", data.getSequenceNumber(),
                eventContext.getPartitionContext().getPartitionId(), count);
    }
}