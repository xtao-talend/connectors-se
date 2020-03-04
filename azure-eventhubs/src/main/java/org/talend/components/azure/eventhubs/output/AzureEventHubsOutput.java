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
package org.talend.components.azure.eventhubs.output;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import javax.annotation.PreDestroy;
import javax.json.JsonBuilderFactory;
import javax.json.JsonReaderFactory;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.spi.JsonProvider;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.EncoderFactory;
import org.talend.components.azure.eventhubs.runtime.converters.AvroConverter;
import org.talend.components.azure.eventhubs.runtime.converters.CSVConverter;
import org.talend.components.azure.eventhubs.runtime.converters.JsonConverter;
import org.talend.components.azure.eventhubs.runtime.converters.RecordConverter;
import org.talend.components.azure.eventhubs.runtime.converters.TextConverter;
import org.talend.components.azure.eventhubs.service.Messages;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.processor.AfterGroup;
import org.talend.sdk.component.api.processor.BeforeGroup;
import org.talend.sdk.component.api.processor.ElementListener;
import org.talend.sdk.component.api.processor.Input;
import org.talend.sdk.component.api.processor.Processor;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.configuration.LocalConfiguration;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import com.microsoft.azure.eventhubs.BatchOptions;
import com.microsoft.azure.eventhubs.ConnectionStringBuilder;
import com.microsoft.azure.eventhubs.EventData;
import com.microsoft.azure.eventhubs.EventDataBatch;
import com.microsoft.azure.eventhubs.EventHubClient;
import com.microsoft.azure.eventhubs.EventHubException;

import lombok.extern.slf4j.Slf4j;

import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_CHARSET;

@Slf4j
@Version
@Icon(Icon.IconType.AZURE_EVENT_HUBS)
@Processor(name = "AzureEventHubsOutput")
@Documentation("AzureEventHubs output")
public class AzureEventHubsOutput implements Serializable {

    private final AzureEventHubsOutputConfiguration configuration;

    private transient List<Record> records;

    private Messages messages;

    private boolean init;

    private ScheduledExecutorService executorService;

    private EventHubClient eventHubClient;

    private RecordConverter recordConverter;

    private GenericDatumWriter<IndexedRecord> datumWriter;

    private BinaryEncoder encoder;

    private RecordBuilderFactory recordBuilderFactory;

    private JsonBuilderFactory jsonBuilderFactory;

    private JsonProvider jsonProvider;

    private JsonReaderFactory readerFactory;

    private Jsonb jsonb;

    public AzureEventHubsOutput(@Option("configuration") final AzureEventHubsOutputConfiguration outputConfig,
            RecordBuilderFactory recordBuilderFactory, JsonBuilderFactory jsonBuilderFactory, JsonProvider jsonProvider,
            JsonReaderFactory readerFactory, Jsonb jsonb, Messages messages) {
        this.configuration = outputConfig;
        this.recordBuilderFactory = recordBuilderFactory;
        this.jsonBuilderFactory = jsonBuilderFactory;
        this.jsonProvider = jsonProvider;
        this.readerFactory = readerFactory;
        this.jsonb = jsonb;
        this.messages = messages;
    }

    @BeforeGroup
    public void beforeGroup() {
        this.records = new ArrayList<>();
    }

    @ElementListener
    public void elementListener(@Input final Record record) throws URISyntaxException, IOException, EventHubException {
        if (!init) {
            // prevent creating db connection if no records
            // it's mostly useful for streaming scenario
            lazyInit();
        }
        records.add(record);
    }

    private void lazyInit() throws URISyntaxException, IOException, EventHubException {
        this.init = true;
        executorService = Executors.newScheduledThreadPool(1);
        final ConnectionStringBuilder connStr = new ConnectionStringBuilder()//
                .setEndpoint(new URI(configuration.getDataset().getConnection().getEndpoint()));
        connStr.setSasKeyName(configuration.getDataset().getConnection().getSasKeyName());
        connStr.setSasKey(configuration.getDataset().getConnection().getSasKey());
        connStr.setEventHubName(configuration.getDataset().getEventHubName());

        eventHubClient = EventHubClient.createSync(connStr.toString(), executorService);

    }

    @AfterGroup
    public void afterGroup() {

        try {
            BatchOptions options = new BatchOptions();
            if (AzureEventHubsOutputConfiguration.PartitionType.COLUMN.equals(configuration.getPartitionType())) {
                options.partitionKey = configuration.getKeyColumn();
            }
            final EventDataBatch events = eventHubClient.createBatch(options);
            for (Record record : records) {
                log.debug(record.toString());
                byte[] payloadBytes = null;
                switch (configuration.getDataset().getValueFormat()) {
                case AVRO: {
                    if (recordConverter == null) {
                        recordConverter = AvroConverter.of(null);
                    }
                    IndexedRecord indexedRecord = ((AvroConverter) recordConverter).fromRecord(record);
                    if (datumWriter == null) {
                        org.apache.avro.Schema.Parser parser = new org.apache.avro.Schema.Parser();
                        Schema schema = parser.parse(configuration.getDataset().getAvroSchema());
                        datumWriter = new GenericDatumWriter<IndexedRecord>(schema);
                    }
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    encoder = EncoderFactory.get().binaryEncoder(out, encoder);
                    datumWriter.write(indexedRecord, encoder);
                    encoder.flush();
                    payloadBytes = out.toByteArray();
                    out.close();
                    break;
                }
                case CSV: {
                    if (recordConverter == null) {
                        recordConverter = CSVConverter.of(null, configuration.getDataset().getFieldDelimiter(), messages);
                    }
                    payloadBytes = ((CSVConverter) recordConverter).fromRecord(record).getBytes(DEFAULT_CHARSET);
                    break;
                }
                case TEXT: {
                    if (recordConverter == null) {
                        recordConverter = TextConverter.of(recordBuilderFactory, messages);
                    }
                    payloadBytes = ((TextConverter) recordConverter).fromRecord(record).getBytes(DEFAULT_CHARSET);
                    break;
                }
                case JSON: {
                    if (recordConverter == null) {
                        recordConverter = JsonConverter.of(recordBuilderFactory, jsonBuilderFactory, jsonProvider, readerFactory,
                                jsonb, messages);
                    }
                    payloadBytes = ((JsonConverter) recordConverter).fromRecord(record).getBytes(DEFAULT_CHARSET);
                    break;
                }
                default:
                    throw new RuntimeException("To be implemented: " + configuration.getDataset().getValueFormat());
                }
                events.tryAdd(EventData.create(payloadBytes));
            }
            eventHubClient.sendSync(events);
        } catch (final Throwable e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @PreDestroy
    public void preDestroy() {
        try {
            if (eventHubClient != null) {
                eventHubClient.closeSync();
            }
            if (executorService != null) {
                executorService.shutdown();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

}