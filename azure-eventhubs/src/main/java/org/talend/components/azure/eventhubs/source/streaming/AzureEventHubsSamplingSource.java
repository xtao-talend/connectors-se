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

import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;

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

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubConsumerClient;
import com.azure.messaging.eventhubs.models.EventPosition;
import com.azure.messaging.eventhubs.models.PartitionEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Documentation("Source to consume eventhubs messages")
public class AzureEventHubsSamplingSource implements Serializable, AzureEventHubsSource {

    private final AzureEventHubsStreamInputConfiguration configuration;

    private ReceiverManager receiverManager;

    private Iterator<PartitionEvent> receivedEvents;

    private EventHubConsumerClient ehClient;

    private Messages messages;

    private transient RecordBuilderFactory recordBuilderFactory;

    private transient RecordConverter recordConverter;

    private transient JsonBuilderFactory jsonBuilderFactory;

    private transient JsonProvider jsonProvider;

    private transient JsonReaderFactory readerFactory;

    private transient Jsonb jsonb;

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
        String endpoint = null;
        if (configuration.getDataset().getConnection().isSpecifyEndpoint()) {
            endpoint = configuration.getDataset().getConnection().getEndpoint();//
        } else {
            endpoint = String.format(Locale.US, ENDPOINT_FORMAT, configuration.getDataset().getConnection().getNamespace(),
                    DEFAULT_DOMAIN_NAME);
        }

        String ehConnString = String.format("Endpoint=%s;SharedAccessKeyName=%s;SharedAccessKey=%s;EntityPath=%s", endpoint,
                configuration.getDataset().getConnection().getSasKeyName(),
                configuration.getDataset().getConnection().getSasKey(), configuration.getDataset().getEventHubName());

        ehClient = new EventHubClientBuilder().connectionString(ehConnString).consumerGroup(configuration.getConsumerGroupName())
                .buildConsumerClient();
        receiverManager = new ReceiverManager();
        List<String> partitionIdList = new ArrayList<String>();
        ehClient.getPartitionIds().forEach(p -> partitionIdList.add(p));
        receiverManager.addPartitions(partitionIdList.toArray(new String[partitionIdList.size()]));
    }

    @Producer
    public Record next() {
        try {
            if (receivedEvents == null || !receivedEvents.hasNext()) {
                log.debug("fetch messages...");
                // TODO let it configurable?
                receivedEvents = receiverManager.getBatchEventData();
                if (receivedEvents == null) {
                    log.debug("no record available now!");
                    return null;
                }
            }
            if (receivedEvents.hasNext()) {
                PartitionEvent partitionEvent = receivedEvents.next();
                // update the position which current partition have read
                EventData eventData = partitionEvent.getData();
                receiverManager.updatePartitionPosition(partitionEvent.getPartitionContext().getPartitionId(),
                        EventPosition.fromSequenceNumber(eventData.getSequenceNumber()));
                Record record = null;
                if (eventData != null) {
                    switch (configuration.getDataset().getValueFormat()) {
                    case AVRO: {
                        if (recordConverter == null) {
                            recordConverter = AvroConverter.of(recordBuilderFactory);
                        }
                        if (schema == null) {
                            schema = new org.apache.avro.Schema.Parser().parse(configuration.getDataset().getAvroSchema());
                            datumReader = new GenericDatumReader<GenericRecord>(schema);
                        }
                        decoder = DecoderFactory.get().binaryDecoder(eventData.getBody(), decoder);
                        record = recordConverter.toRecord(datumReader.read(null, decoder));
                        break;
                    }
                    case CSV: {
                        if (recordConverter == null) {
                            recordConverter = CSVConverter.of(recordBuilderFactory,
                                    configuration.getDataset().getFieldDelimiter(), messages);
                        }
                        record = recordConverter.toRecord(eventData.getBodyAsString());
                        break;
                    }
                    case TEXT: {
                        if (recordConverter == null) {
                            recordConverter = TextConverter.of(recordBuilderFactory, messages);
                        }
                        record = recordConverter.toRecord(eventData.getBodyAsString());
                        break;
                    }
                    case JSON: {
                        if (recordConverter == null) {
                            recordConverter = JsonConverter.of(recordBuilderFactory, jsonBuilderFactory, jsonProvider,
                                    readerFactory, jsonb, messages);
                        }
                        record = recordConverter.toRecord(eventData.getBodyAsString());
                        break;
                    }
                    default:
                        throw new RuntimeException("To be implemented: " + configuration.getDataset().getValueFormat());
                    }
                    log.debug(record.toString());
                }
                System.out.println(record.toString());
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
        if (receiverManager != null) {
            receiverManager.closeAll();
        }
        if (ehClient != null) {
            ehClient.close();
        }
    }

    class ReceiverManager {

        private Map<String, EventPosition> eventPositionMap;

        private Queue<String> partitionInQueue;

        Iterator<PartitionEvent> events;

        ReceiverManager() {
            this.eventPositionMap = new LinkedHashMap<>();
            this.partitionInQueue = new LinkedList<>();
        }

        void addPartitions(String... partitionIds) {
            for (String partitionId : partitionIds) {
                // This would check whether position config is validate or not at the moment
                if (!eventPositionMap.containsKey(partitionId)) {
                    try {
                        EventPosition position = EventPosition.earliest();
                        receiverManager.updatePartitionPosition(partitionId, position);
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

        boolean isReceiverAvailable() {
            // eventPositionMap and partitionInQueue should not empty
            if ((events == null || !events.hasNext()) && !this.eventPositionMap.isEmpty()) {
                while (!partitionInQueue.isEmpty()) {
                    String partitionId = partitionInQueue.peek();
                    if (partitionId != null && eventPositionMap.get(partitionId) == null) {
                        // No available position to create receiver. continue check next
                        continue;
                    } else {
                        // TODO batch size and read timeout configurable ?
                        System.out.println(eventPositionMap.get(partitionId));
                        events = ehClient.receiveFromPartition(partitionId, 100, eventPositionMap.get(partitionId),
                                Duration.ofMillis(1000)).iterator();
                        if (events != null && events.hasNext()) {
                            return true;
                        } else {
                            // pool the partition id which data have been read
                            partitionInQueue.poll();
                            continue;
                        }
                    }
                }
            }
            return events != null && events.hasNext();
        }

        void updatePartitionPosition(String partitionId, EventPosition position) {
            eventPositionMap.put(partitionId, position);
        }

        Iterator<PartitionEvent> getBatchEventData() {
            while (isReceiverAvailable()) {
                return this.events;
            }
            return null;
        }

        void closeAll() {
            eventPositionMap.clear();
            partitionInQueue.clear();
        }

    }

}