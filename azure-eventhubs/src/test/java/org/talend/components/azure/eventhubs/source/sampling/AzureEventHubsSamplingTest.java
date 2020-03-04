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

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ThreadLocalRandom;

import org.junit.ClassRule;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.talend.components.azure.eventhubs.AzureEventHubsTestBase;
import org.talend.components.azure.eventhubs.dataset.AzureEventHubsDataSet;
import org.talend.components.azure.eventhubs.source.streaming.AzureEventHubsStreamInputConfiguration;
import org.talend.components.azure.eventhubs.source.streaming.AzureEventHubsStreamInputMapper;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.junit.ComponentsHandler;
import org.talend.sdk.component.junit.SimpleComponentRule;
import org.talend.sdk.component.junit5.Injected;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.runtime.input.Mapper;

import junit.framework.TestSuite;
import lombok.extern.slf4j.Slf4j;

/**
 * Note: as event data can't be deleted by API, so need drop this event hub and recreate before start the unit test
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Disabled("Run manually follow the comment")
@WithComponents("org.talend.components.azure.eventhubs")
@Slf4j
class AzureEventHubsSamplingTest extends AzureEventHubsTestBase {

    @Injected
    private ComponentsHandler handler;

    protected static final String EVENTHUB_NAME = "eh-source-test-avro";

    private static final String UNIQUE_ID;

    static {
        UNIQUE_ID = Integer.toString(ThreadLocalRandom.current().nextInt(1, 100000));
    }

    @Test
    public void manualMapper() {
        AzureEventHubsStreamInputConfiguration inputConfiguration = new AzureEventHubsStreamInputConfiguration();
        final AzureEventHubsDataSet dataSet = new AzureEventHubsDataSet();
        dataSet.setConnection(getDataStore());
        dataSet.setEventHubName(EVENTHUB_NAME);
        dataSet.setValueFormat(AzureEventHubsDataSet.ValueFormat.AVRO);
        dataSet.setAvroSchema(
                "{\"type\":\"record\",\"name\":\"LongList\",\"aliases\":[\"LinkedLongs\"],\"fields\":[{\"name\":\"pk\",\"type\":\"string\"},{\"name\":\"name\",\"type\":\"string\"}]}");

        inputConfiguration.setConsumerGroupName(CONSUME_GROUP);
        inputConfiguration.setDataset(dataSet);
        inputConfiguration.setSampling(true);

        final Mapper mapper = handler.createMapper(AzureEventHubsStreamInputMapper.class, inputConfiguration);
        assertTrue(mapper.isStream());
        assertEquals(asList(/* TODO - give the expected data */), handler.collectAsList(Record.class, mapper));
    }

}