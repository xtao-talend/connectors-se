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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.talend.components.azure.eventhubs.AzureEventHubsRWTestBase;
import org.talend.components.azure.eventhubs.dataset.AzureEventHubsDataSet;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.runtime.input.Mapper;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithComponents("org.talend.components.azure.eventhubs")
class AzureEventHubsInputConfigurationTest extends AzureEventHubsRWTestBase {

    protected static final String CONSUMER_GROUP = "fake-consumer";

    protected static final String CONTAINER_NAME = "fake-container";

    @Test
    @DisplayName("Test missing checkpoint connection")
    void testMissingCheckpointConnection() {
        AzureEventHubsStreamInputConfiguration inputConfiguration = createInputConfiguration(false);
        inputConfiguration.setDataset(createDataSet());

        inputConfiguration.setCheckpointStore(null);
        inputConfiguration.setConsumerGroupName(CONSUMER_GROUP);
        inputConfiguration.setContainerName(CONTAINER_NAME);

        final Mapper mapper = getComponentsHandler().createMapper(AzureEventHubsStreamInputMapper.class, inputConfiguration);
        assertTrue(mapper.isStream());
        getComponentsHandler().start();
        assertThrows(IllegalArgumentException.class, () -> {
            getComponentsHandler().collectAsList(Record.class, mapper, 1);
        });

    }

    @Test
    @DisplayName("Test missing container name")
    void testMissingContainerName() {
        AzureEventHubsStreamInputConfiguration inputConfiguration = createInputConfiguration(false);
        inputConfiguration.setDataset(createDataSet());

        inputConfiguration.setConsumerGroupName(CONSUMER_GROUP);
        inputConfiguration.setContainerName(null);

        final Mapper mapper = getComponentsHandler().createMapper(AzureEventHubsStreamInputMapper.class, inputConfiguration);
        assertTrue(mapper.isStream());
        getComponentsHandler().start();
        assertThrows(IllegalArgumentException.class, () -> {
            getComponentsHandler().collectAsList(Record.class, mapper, 1);
        });

    }

    @Test
    @DisplayName("Test missing consumer group")
    void testMissingConsumerGroup() {
        AzureEventHubsStreamInputConfiguration inputConfiguration = createInputConfiguration(false);
        inputConfiguration.setDataset(createDataSet());

        inputConfiguration.setConsumerGroupName("");
        inputConfiguration.setContainerName(CONTAINER_NAME);

        final Mapper mapper = getComponentsHandler().createMapper(AzureEventHubsStreamInputMapper.class, inputConfiguration);
        assertTrue(mapper.isStream());
        getComponentsHandler().start();
        assertThrows(IllegalArgumentException.class, () -> {
            getComponentsHandler().collectAsList(Record.class, mapper, 1);
        });

    }

    @Test
    @DisplayName("Test missing enqueued datetime")
    void testMissingEnqueuedDateTime() {
        AzureEventHubsStreamInputConfiguration inputConfiguration = createInputConfiguration(false);
        inputConfiguration.setDataset(createDataSet());

        inputConfiguration.setConsumerGroupName(CONSUMER_GROUP);
        inputConfiguration.setContainerName(CONTAINER_NAME);
        inputConfiguration.setAutoOffsetReset(AzureEventHubsStreamInputConfiguration.OffsetResetStrategy.DATETIME);
        inputConfiguration.setEnqueuedDateTime(null);

        final Mapper mapper = getComponentsHandler().createMapper(AzureEventHubsStreamInputMapper.class, inputConfiguration);
        assertTrue(mapper.isStream());
        getComponentsHandler().start();
        assertThrows(IllegalArgumentException.class, () -> {
            getComponentsHandler().collectAsList(Record.class, mapper, 1);
        });

    }

    @Override
    protected AzureEventHubsDataSet createDataSet() {
        AzureEventHubsStreamInputConfiguration inputConfiguration = new AzureEventHubsStreamInputConfiguration();
        final AzureEventHubsDataSet dataSet = new AzureEventHubsDataSet();
        dataSet.setConnection(getDataStore());
        dataSet.setValueFormat(AzureEventHubsDataSet.ValueFormat.CSV);
        dataSet.setEventHubName(CONSUMER_GROUP);
        return dataSet;
    }

    @Override
    protected List<Record> generateSampleData(int index) {
        throw new IllegalStateException("Not implemented!");
    }
}