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

import java.io.Serializable;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.talend.components.azure.common.Protocol;
import org.talend.components.azure.common.connection.AzureStorageConnectionAccount;
import org.talend.components.azure.eventhubs.dataset.AzureEventHubsDataSet;
import org.talend.components.azure.eventhubs.datastore.AzureEventHubsDataStore;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.junit.BaseComponentsHandler;
import org.talend.sdk.component.junit5.Injected;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.runtime.input.Mapper;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithComponents("org.talend.components.azure.eventhubs")
class AzureEventHubsInputConfigurationTest implements Serializable {

    protected static final String CONSUMER_GROUP = "fake-consumer";

    protected static final String CONTAINER_NAME = "fake-container";

    private static final String ACCOUNT_NAME = "fake-account";

    private static final String ACCOUNT_KEY = "fake-key";

    protected static final String EVENTHUB_NAME = "fake-eventhub";

    private static final String SAS_KEY = "fake-key";

    private static final String SAS_KEY_NAME = "fake-key-name";

    @Injected
    private BaseComponentsHandler componentsHandler;

    @Test
    @DisplayName("Test missing checkpoint connection")
    void testMissingCheckpointConnection() {
        AzureEventHubsStreamInputConfiguration inputConfiguration = createInputConfiguration();

        inputConfiguration.setCheckpointStore(null);
        inputConfiguration.setConsumerGroupName(CONSUMER_GROUP);
        inputConfiguration.setContainerName(CONTAINER_NAME);

        final Mapper mapper = getComponentsHandler().createMapper(AzureEventHubsStreamInputMapper.class, inputConfiguration);
        assertThrows(IllegalArgumentException.class, () -> {
            getComponentsHandler().collectAsList(Record.class, mapper, 1);
        });

    }

    @Test
    @DisplayName("Test missing container name")
    void testMissingContainerName() {
        AzureEventHubsStreamInputConfiguration inputConfiguration = createInputConfiguration();

        inputConfiguration.setConsumerGroupName(CONSUMER_GROUP);
        inputConfiguration.setContainerName(null);

        final Mapper mapper = getComponentsHandler().createMapper(AzureEventHubsStreamInputMapper.class, inputConfiguration);
        assertThrows(IllegalArgumentException.class, () -> {
            getComponentsHandler().collectAsList(Record.class, mapper, 1);
        });

    }

    @Test
    @DisplayName("Test missing consumer group")
    void testMissingConsumerGroup() {
        AzureEventHubsStreamInputConfiguration inputConfiguration = createInputConfiguration();

        inputConfiguration.setConsumerGroupName("");
        inputConfiguration.setContainerName(CONTAINER_NAME);

        final Mapper mapper = getComponentsHandler().createMapper(AzureEventHubsStreamInputMapper.class, inputConfiguration);
        assertThrows(IllegalArgumentException.class, () -> {
            getComponentsHandler().collectAsList(Record.class, mapper, 1);
        });

    }

    @Test
    @DisplayName("Test missing enqueued datetime")
    void testMissingEnqueuedDateTime() {
        AzureEventHubsStreamInputConfiguration inputConfiguration = createInputConfiguration();

        inputConfiguration.setConsumerGroupName(CONSUMER_GROUP);
        inputConfiguration.setContainerName(CONTAINER_NAME);
        inputConfiguration.setAutoOffsetReset(AzureEventHubsStreamInputConfiguration.OffsetResetStrategy.DATETIME);
        inputConfiguration.setEnqueuedDateTime(null);

        final Mapper mapper = getComponentsHandler().createMapper(AzureEventHubsStreamInputMapper.class, inputConfiguration);
        assertThrows(IllegalArgumentException.class, () -> {
            getComponentsHandler().collectAsList(Record.class, mapper, 1);
        });

    }

    protected AzureEventHubsStreamInputConfiguration createInputConfiguration() {
        AzureEventHubsStreamInputConfiguration inputConfiguration = new AzureEventHubsStreamInputConfiguration();
        AzureEventHubsDataSet dataSet = new AzureEventHubsDataSet();
        AzureEventHubsDataStore dataStore = new AzureEventHubsDataStore();
        dataStore.setAuthMethod(AzureEventHubsDataStore.AuthMethod.SAS);
        dataStore.setSasKey(SAS_KEY);
        dataStore.setSasKeyName(SAS_KEY_NAME);
        dataSet.setConnection(dataStore);
        dataSet.setEventHubName(EVENTHUB_NAME);
        inputConfiguration.setDataset(dataSet);

        CheckpointStoreConfiguration storageAccount = new CheckpointStoreConfiguration();
        storageAccount.setUseAzureSharedSignature(false);
        AzureStorageConnectionAccount accountConn = new AzureStorageConnectionAccount();
        accountConn.setAccountName(ACCOUNT_NAME);
        accountConn.setAccountKey(ACCOUNT_KEY);
        accountConn.setProtocol(Protocol.HTTPS);
        storageAccount.setAccountConnection(accountConn);
        return inputConfiguration;
    }

}