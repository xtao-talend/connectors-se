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
package org.talend.components.azure.eventhubs.source.sampling;

import static org.talend.sdk.component.junit.SimpleFactory.configurationByExample;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.talend.components.azure.eventhubs.AzureEventHubsTestBase;
import org.talend.components.azure.eventhubs.dataset.AzureEventHubsDataSet;
import org.talend.components.azure.eventhubs.output.AzureEventHubsOutputConfiguration;
import org.talend.components.azure.eventhubs.source.streaming.AzureEventHubsStreamInputConfiguration;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.runtime.manager.chain.Job;

import lombok.extern.slf4j.Slf4j;

/**
 * Note: as event data can't be deleted by API, so need drop this event hub and recreate before start the unit test
 */
@Slf4j
@Disabled("Run manually follow the comment")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithComponents("org.talend.components.azure.eventhubs")
class AzureEventHubsSamplingCSVTest extends AzureEventHubsTestBase {

    protected static final String EVENTHUB_NAME = "eh-source-test";

    private static final String UNIQUE_ID;

    static {
        UNIQUE_ID = Integer.toString(ThreadLocalRandom.current().nextInt(1, 100000));
    }

    @BeforeAll
    void prepareData() {
        log.warn("a) Eventhub \"" + EVENTHUB_NAME + "\" was created ? ");
        log.warn("b) Partition count is 6 ? ");
        log.warn("c) Consume group \"" + CONSUME_GROUP + "\" ?");
        for (int index = 0; index < 6; index++) {
            AzureEventHubsOutputConfiguration outputConfiguration = new AzureEventHubsOutputConfiguration();
            final AzureEventHubsDataSet dataSet = new AzureEventHubsDataSet();
            dataSet.setEventHubName(EVENTHUB_NAME);
            dataSet.setConnection(getDataStore());

            outputConfiguration.setDataset(dataSet);

            RecordBuilderFactory factory = getComponentsHandler().findService(RecordBuilderFactory.class);
            List<Record> records = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                records.add(factory.newRecordBuilder().withString("pk", "talend_pk_1")
                        .withString("Name", "TestName_" + i + "_" + UNIQUE_ID).build());
            }

            final String config = configurationByExample().forInstance(outputConfiguration).configured().toQueryString();
            getComponentsHandler().setInputData(records);
            Job.components().component("emitter", "test://emitter")
                    .component("azureeventhubs-output", "AzureEventHubs://AzureEventHubsOutput?" + config).connections()
                    .from("emitter").to("azureeventhubs-output").build().run();
            getComponentsHandler().resetState();
        }
    }

    @Test
    @DisplayName("test sampling csv format")
    void testSamplingCsvFormat() {

        AzureEventHubsStreamInputConfiguration inputConfiguration = new AzureEventHubsStreamInputConfiguration();
        final AzureEventHubsDataSet dataSet = new AzureEventHubsDataSet();
        dataSet.setConnection(getDataStore());
        dataSet.setEventHubName(EVENTHUB_NAME);
        dataSet.setValueFormat(AzureEventHubsDataSet.ValueFormat.CSV);
        dataSet.setFieldDelimiter(AzureEventHubsDataSet.FieldDelimiterType.SEMICOLON);

        inputConfiguration.setConsumerGroupName(CONSUME_GROUP);

        inputConfiguration.setDataset(dataSet);
        inputConfiguration.setSampling(true);

        final String config = configurationByExample().forInstance(inputConfiguration).configured().toQueryString()
                + "&sampling=true";
        Job.components().component("azureeventhubs-input", "AzureEventHubs://AzureEventHubsInputStream?" + config)
                .component("collector", "test://collector").connections().from("azureeventhubs-input").to("collector").build()
                .run();
        final List<Record> records = getComponentsHandler().getCollectedData(Record.class);
        Assert.assertNotNull(records);
    }
}