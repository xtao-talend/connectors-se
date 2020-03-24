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

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.talend.components.azure.eventhubs.dataset.AzureEventHubsDataSet;
import org.talend.components.azure.eventhubs.AzureEventHubsRWTestBase;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.runtime.input.Mapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Disabled("Azure eventhubs credentials is not ready on ci")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithComponents("org.talend.components.azure.eventhubs")
class AzureEventHubsJsonTest extends AzureEventHubsRWTestBase {

    protected static final String EVENTHUB_NAME = "eh-junit-json";

    protected static final int PARTITION_COUNT = 4;

    protected static final int RECORD_PER_PARTITION = 20;

    private static final String UNIQUE_ID;

    static {
        UNIQUE_ID = Integer.toString(ThreadLocalRandom.current().nextInt(1, 100000));
    }

    @BeforeAll
    void prepareData() {
        prepareData(EVENTHUB_NAME, CONSUME_GROUP, PARTITION_COUNT);
    }

    @Test
    @DisplayName("Read Json format data start from Earliest")
    void testReadEarliest() {
        // here expect 4*20
        int maxRecords = PARTITION_COUNT * RECORD_PER_PARTITION;
        final String containerName = "eh-json-read-earliest";
        AzureEventHubsStreamInputConfiguration inputConfiguration = createInputConfiguration(true);

        inputConfiguration.setConsumerGroupName(CONSUME_GROUP);
        inputConfiguration.setContainerName(containerName);
        inputConfiguration.setAutoOffsetReset(AzureEventHubsStreamInputConfiguration.OffsetResetStrategy.EARLIEST);
        // default is 5
        inputConfiguration.setCommitOffsetEvery(5);

        final Mapper mapper = getComponentsHandler().createMapper(AzureEventHubsStreamInputMapper.class, inputConfiguration);
        assertTrue(mapper.isStream());
        getComponentsHandler().start();
        List<Record> records = getComponentsHandler().collectAsList(Record.class, mapper, maxRecords);
        assertEquals(maxRecords, records.size());

        getComponentsHandler().resetState();
    }

    @Test
    @Disabled("need make sampling is serializable")
    @DisplayName("test sampling Json format")
    void testSamplingJsonFormat() {

        // here expect 4*20
        int maxRecords = PARTITION_COUNT * RECORD_PER_PARTITION;
        AzureEventHubsStreamInputConfiguration inputConfiguration = new AzureEventHubsStreamInputConfiguration();
        inputConfiguration.setDataset(createDataSet());

        inputConfiguration.setConsumerGroupName(CONSUME_GROUP);
        inputConfiguration.setSampling(true);
        inputConfiguration.setAutoOffsetReset(AzureEventHubsStreamInputConfiguration.OffsetResetStrategy.EARLIEST);

        final Mapper mapper = getComponentsHandler().createMapper(AzureEventHubsStreamInputMapper.class, inputConfiguration);
        getComponentsHandler().start();
        List<Record> records = getComponentsHandler().collectAsList(Record.class, mapper, maxRecords);
        assertEquals(maxRecords, records.size());
    }

    @Test
    @Disabled("need wait message write to eventhub")
    void testReadTimeout() {
        // here expect 500
        int maxRecords = PARTITION_COUNT * RECORD_PER_PARTITION;
        final String containerName = "eh-json-read-latest";
        AzureEventHubsStreamInputConfiguration inputConfiguration = createInputConfiguration(false);

        inputConfiguration.setConsumerGroupName(CONSUME_GROUP);
        inputConfiguration.setContainerName(containerName);
        // read latest
        inputConfiguration.setAutoOffsetReset(AzureEventHubsStreamInputConfiguration.OffsetResetStrategy.LATEST);
        // default is 5
        inputConfiguration.setCommitOffsetEvery(5);

        final Mapper mapper = getComponentsHandler().createMapper(AzureEventHubsStreamInputMapper.class, inputConfiguration);
        assertTrue(mapper.isStream());
        getComponentsHandler().start();
        List<Record> records = getComponentsHandler().collectAsList(Record.class, mapper, maxRecords);
        assertEquals(maxRecords, records.size());
        getComponentsHandler().resetState();
    }

    /**
     * Get dataset with JSON value format
     */
    protected AzureEventHubsDataSet createDataSet() {
        final AzureEventHubsDataSet dataSet = new AzureEventHubsDataSet();
        dataSet.setEventHubName(EVENTHUB_NAME);
        dataSet.setConnection(getDataStore());
        dataSet.setValueFormat(AzureEventHubsDataSet.ValueFormat.JSON);
        return dataSet;
    }

    protected List<Record> generateSampleData(int index) {
        RecordBuilderFactory factory = getComponentsHandler().findService(RecordBuilderFactory.class);
        List<Record> records = new ArrayList<>();
        for (int i = 0; i < RECORD_PER_PARTITION; i++) {
            int seq = RECORD_PER_PARTITION * index + i;
            records.add(factory.newRecordBuilder().withString("pk", "talend_pk_" + seq)
                    .withString("Name", "TestName_" + seq + "_" + UNIQUE_ID).build());
        }
        return records;
    }
}