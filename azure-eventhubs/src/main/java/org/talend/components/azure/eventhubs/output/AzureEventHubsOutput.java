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

import static com.azure.messaging.eventhubs.implementation.ClientConstants.ENDPOINT_FORMAT;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_CHARSET;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_DOMAIN_NAME;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.PreDestroy;
import javax.json.JsonBuilderFactory;
import javax.json.JsonReaderFactory;
import javax.json.bind.Jsonb;
import javax.json.spi.JsonProvider;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumWriter;
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
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import com.azure.messaging.eventhubs.EventData;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import com.azure.messaging.eventhubs.models.CreateBatchOptions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Version
@Icon(value = Icon.IconType.CUSTOM, custom = "azure-event-hubs")
@Processor(name = "AzureEventHubsOutput")
@Documentation("AzureEventHubs output")
public class AzureEventHubsOutput implements Serializable {

    private final AzureEventHubsOutputConfiguration configuration;

    private transient List<Record> records;

    private Messages messages;

    private boolean init;

    private EventHubProducerClient eventHubClient;

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
    public void elementListener(@Input final Record record) {
        if (!init) {
            // prevent creating db connection if no records
            // it's mostly useful for streaming scenario
            lazyInit();
        }
        records.add(record);
    }

    private void lazyInit() {
        this.init = true;
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

        eventHubClient = new EventHubClientBuilder().connectionString(ehConnString).buildProducerClient();

    }

    @AfterGroup
    public void afterGroup() {

        try {
            EventDataBatch events = null;
            if (AzureEventHubsOutputConfiguration.PartitionType.COLUMN.equals(configuration.getPartitionType())) {
                CreateBatchOptions options = new CreateBatchOptions();
                options.setPartitionKey(configuration.getKeyColumn());
                events = eventHubClient.createBatch(options);
            } else {
                events = eventHubClient.createBatch();
            }
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
                events.tryAdd(new EventData(payloadBytes));
            }
            eventHubClient.send(events);
        } catch (final Throwable e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    @PreDestroy
    public void preDestroy() {
        try {
            if (eventHubClient != null) {
                eventHubClient.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

}