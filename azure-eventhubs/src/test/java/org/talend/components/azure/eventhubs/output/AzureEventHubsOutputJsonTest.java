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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.talend.sdk.component.junit.SimpleFactory.configurationByExample;

import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.beam.sdk.Pipeline;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.talend.components.azure.eventhubs.AzureEventHubsRWTestBase;
import org.talend.components.azure.eventhubs.dataset.AzureEventHubsDataSet;
import org.talend.components.azure.eventhubs.source.streaming.AzureEventHubsStreamInputConfiguration;
import org.talend.components.azure.eventhubs.source.streaming.AzureEventHubsStreamInputMapper;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.runtime.input.Mapper;
import org.talend.sdk.component.runtime.manager.chain.Job;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Disabled("Run manually follow the comment")
@WithComponents("org.talend.components.azure.eventhubs")
class AzureEventHubsOutputJsonTest extends AzureEventHubsRWTestBase {

    protected static final String EVENTHUB_NAME = "eh-junit-out-json";

    protected static final int PARTITION_COUNT = 3;

    protected static final int RECORD_PER_PARTITION = 10;

    @Service
    private RecordBuilderFactory factory;

    @Test
    void testSimpleSend() {
        String uniqueId = getUniqueID();
        log.warn("a) Eventhub \"" + EVENTHUB_NAME + "\" was created ? ");
        log.warn("b) Partition count is " + PARTITION_COUNT + " ? ");
        log.warn("c) Consume group \"" + CONSUME_GROUP + "\" ?");

        List<String> expectStringList = new ArrayList<>();
        for (int index = 0; index < PARTITION_COUNT; index++) {
            // Write data with default partition type "ROUND_ROBIN"
            AzureEventHubsOutputConfiguration outputConfiguration = createOutputConfiguration();
            List<Record> records = generateSampleData(index, uniqueId);
            records.stream().forEach(record -> expectStringList.add(record.toString()));

            final String config = configurationByExample().forInstance(outputConfiguration).configured().toQueryString();
            getComponentsHandler().setInputData(records);
            Job.components().component("emitter", "test://emitter")
                    .component("azureeventhubs-output", "AzureEventHubs://AzureEventHubsOutput?" + config).connections()
                    .from("emitter").to("azureeventhubs-output").build().run();
            getComponentsHandler().resetState();

        }
        checkRecords(expectStringList);

    }

    @Test
    void testFirstColumnEmpty() {
        String uniqueId = getUniqueID();
        List<String> expectStringList = new ArrayList<>();
        for (int index = 0; index < PARTITION_COUNT; index++) {
            // Write data with default partition type "ROUND_ROBIN"
            AzureEventHubsOutputConfiguration outputConfiguration = createOutputConfiguration();

            List<Record> records = new ArrayList<>();
            Schema schema = factory.newSchemaBuilder(Schema.Type.RECORD)
                    .withEntry(factory.newEntryBuilder().withName("Id").withType(Schema.Type.STRING).withNullable(true).build())
                    .withEntry(factory.newEntryBuilder().withName("Name").withType(Schema.Type.STRING).build()).build();

            for (int i = 0; i < RECORD_PER_PARTITION; i++) {
                Record record = factory.newRecordBuilder(schema).withString("Id", "")
                        .withString("Name", "TestName_" + i + "_" + uniqueId).build();
                records.add(record);
                expectStringList.add(record.toString());
            }
            final String config = configurationByExample().forInstance(outputConfiguration).configured().toQueryString();
            getComponentsHandler().setInputData(records);
            Job.components().component("emitter", "test://emitter")
                    .component("azureeventhubs-output", "AzureEventHubs://AzureEventHubsOutput?" + config).connections()
                    .from("emitter").to("azureeventhubs-output").build().run();
            getComponentsHandler().resetState();

        }
        checkRecords(expectStringList);
    }

    @Test
    void testAllType() {
        String uniqueId = getUniqueID();
        AzureEventHubsOutputConfiguration outputConfiguration = createOutputConfiguration();

        List<Record> records = new ArrayList<>();
        List<String> expectStringList = new ArrayList<>();
        Schema schema = factory.newSchemaBuilder(Schema.Type.RECORD)
                .withEntry(
                        factory.newEntryBuilder().withName("test_string").withType(Schema.Type.STRING).withNullable(true).build())
                .withEntry(factory.newEntryBuilder().withName("test_boolean").withType(Schema.Type.BOOLEAN).withNullable(true)
                        .build())
                .withEntry(
                        factory.newEntryBuilder().withName("test_double").withType(Schema.Type.DOUBLE).withNullable(true).build())
                .withEntry(
                        factory.newEntryBuilder().withName("test_float").withType(Schema.Type.FLOAT).withNullable(true).build())
                .withEntry(factory.newEntryBuilder().withName("test_long").withType(Schema.Type.LONG).withNullable(true).build())
                .withEntry(factory.newEntryBuilder().withName("test_int").withType(Schema.Type.INT).withNullable(true).build())
                .withEntry(factory.newEntryBuilder().withName("test_datetime").withType(Schema.Type.DATETIME).withNullable(true)
                        .build())
                .withEntry(
                        factory.newEntryBuilder().withName("test_bytes").withType(Schema.Type.BYTES).withNullable(true).build())
                .build();

        // empty value
        Record record1 = factory.newRecordBuilder(schema).withString("test_string", "test_string_" + uniqueId)
                .withBoolean("test_boolean", false).withDouble("test_double", 0.25).withFloat("test_float", 0.25f)
                .withLong("test_long", 1000L).withInt("test_int", 100)
                .withDateTime("test_datetime", ZonedDateTime.ofInstant(Instant.ofEpochMilli(1573840145251L), ZoneOffset.UTC))
                .withBytes("test_bytes", "test_bytes".getBytes(Charset.forName("UTF-8"))).build();
        records.add(record1);
        // null value
        Record record2 = factory.newRecordBuilder(schema).withString("test_string", "test_string_" + uniqueId).build();
        records.add(record2);

        expectStringList.add("AvroRecord{delegate={\"test_string\": \"test_string_" + uniqueId
                + "\", \"test_boolean\": false, \"test_double\": 0.25, \"test_float\": 0.25, "
                + "\"test_long\": 1000.0, \"test_int\": 100.0, \"test_datetime\": \"2019-11-15T17:49:05.251Z[UTC]\", \"test_bytes\": \"dGVzdF9ieXRlcw==\"}}");
        expectStringList.add("AvroRecord{delegate={\"test_string\": \"test_string_" + uniqueId + "\"}}");

        final String config = configurationByExample().forInstance(outputConfiguration).configured().toQueryString();
        getComponentsHandler().setInputData(records);
        Job.components().component("emitter", "test://emitter")
                .component("azureeventhubs-output", "AzureEventHubs://AzureEventHubsOutput?" + config).connections()
                .from("emitter").to("azureeventhubs-output").build().run();
        getComponentsHandler().resetState();

        checkRecords(expectStringList);

    }

    @Test
    void testPartitionKeyColumn() {
        String uniqueId = getUniqueID();
        AzureEventHubsOutputConfiguration outputConfiguration = createOutputConfiguration();

        outputConfiguration.setPartitionType(AzureEventHubsOutputConfiguration.PartitionType.COLUMN);
        outputConfiguration.setKeyColumn("Id");

        List<Record> records = new ArrayList<>();
        List<String> expectStringList = new ArrayList<>();
        for (int i = 0; i < 6 * RECORD_PER_PARTITION; i++) {
            // every 20 records have same Id value
            Record record = factory.newRecordBuilder().withString("Id", String.valueOf((i / 20) + 1))
                    .withString("Name", "TestName_" + i + "_" + uniqueId).build();
            records.add(record);
            expectStringList.add(record.toString());
        }

        final String config = configurationByExample().forInstance(outputConfiguration).configured().toQueryString();
        getComponentsHandler().setInputData(records);
        Job.components().component("emitter", "test://emitter")
                .component("azureeventhubs-output", "AzureEventHubs://AzureEventHubsOutput?$maxBatchSize=20&" + config)
                .connections().from("emitter").to("azureeventhubs-output").build().run();
        getComponentsHandler().resetState();

        checkRecords(expectStringList);

    }

    protected AzureEventHubsDataSet createDataSet() {
        final AzureEventHubsDataSet dataSet = new AzureEventHubsDataSet();
        dataSet.setConnection(getDataStore());
        dataSet.setValueFormat(AzureEventHubsDataSet.ValueFormat.JSON);
        dataSet.setEventHubName(EVENTHUB_NAME);
        return dataSet;
    }

    @Override
    protected List<Record> generateSampleData(int index) {
        return null;
    }

    protected List<Record> generateSampleData(int index, String uniqueId) {
        List<Record> records = new ArrayList<>();
        for (int i = 0; i < RECORD_PER_PARTITION; i++) {
            int seq = RECORD_PER_PARTITION * index + i;
            records.add(factory.newRecordBuilder().withString("Id", "ID_" + i + "_" + seq)
                    .withString("Name", "TestName_" + seq + "_" + uniqueId).build());
        }
        return records;
    }

    void checkRecords(List<String> expected) {
        // here expect 3*10
        int maxRecords = expected.size();

        final String containerName = "eh-check-json-write";
        AzureEventHubsStreamInputConfiguration inputConfiguration = createInputConfiguration(false);

        inputConfiguration.setConsumerGroupName(CONSUME_GROUP);
        inputConfiguration.setContainerName(containerName);
        inputConfiguration.setAutoOffsetReset(AzureEventHubsStreamInputConfiguration.OffsetResetStrategy.EARLIEST);
        inputConfiguration.setCommitOffsetEvery(maxRecords < 5 ? 1 : 5);

        final Mapper mapper = getComponentsHandler().createMapper(AzureEventHubsStreamInputMapper.class, inputConfiguration);
        assertTrue(mapper.isStream());
        getComponentsHandler().start();
        List<String> rString = new ArrayList<>();
        getComponentsHandler().collect(Record.class, mapper, maxRecords).forEach(record -> rString.add(record.toString()));
        assertTrue(expected.containsAll(rString));
        getComponentsHandler().resetState();
    }

}