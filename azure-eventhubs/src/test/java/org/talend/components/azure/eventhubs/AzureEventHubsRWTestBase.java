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
package org.talend.components.azure.eventhubs;

import static org.talend.sdk.component.junit.SimpleFactory.configurationByExample;

import java.util.List;

import org.talend.components.azure.common.Protocol;
import org.talend.components.azure.common.connection.AzureStorageConnectionAccount;
import org.talend.components.azure.common.connection.AzureStorageConnectionSignature;
import org.talend.components.azure.eventhubs.dataset.AzureEventHubsDataSet;
import org.talend.components.azure.eventhubs.output.AzureEventHubsOutputConfiguration;
import org.talend.components.azure.eventhubs.source.streaming.AzureEventHubsStreamInputConfiguration;
import org.talend.components.azure.eventhubs.source.streaming.CheckpointStoreConfiguration;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.runtime.manager.chain.Job;

import lombok.extern.slf4j.Slf4j;

/**
 * Eventhub read & write base
 */
@Slf4j
public abstract class AzureEventHubsRWTestBase extends AzureEventHubsTestBase {

    /**
     * Prepare test data
     * 
     * @param eventhubName the target eventhub name
     * @param consumeGroup consume group name
     * @param partitionCount the number of the partitions in the eventhub
     */
    protected void prepareData(String eventhubName, String consumeGroup, int partitionCount) {
        log.warn("a) Eventhub \"" + eventhubName + "\" was created ? ");
        log.warn("b) Partition count is " + partitionCount + " ? ");
        log.warn("c) Consume group \"" + consumeGroup + "\" ?");
        for (int index = 0; index < partitionCount; index++) {
            // Write data with default partition type "ROUND_ROBIN"
            AzureEventHubsOutputConfiguration outputConfiguration = createOutputConfiguration();
            List<Record> records = generateSampleData(index);

            final String config = configurationByExample().forInstance(outputConfiguration).configured().toQueryString();
            getComponentsHandler().setInputData(records);
            Job.components().component("emitter", "test://emitter")
                    .component("azureeventhubs-output", "AzureEventHubs://AzureEventHubsOutput?" + config).connections()
                    .from("emitter").to("azureeventhubs-output").build().run();
            getComponentsHandler().resetState();
        }
    }

    /**
     * Create a default azure storage connection properties
     * 
     * @return
     */
    protected CheckpointStoreConfiguration getAzureStorageConnection(boolean useSAS) {

        CheckpointStoreConfiguration storageAccount = new CheckpointStoreConfiguration();
        storageAccount.setUseAzureSharedSignature(useSAS);
        if (useSAS) {
            AzureStorageConnectionSignature sasConn = new AzureStorageConnectionSignature();
            sasConn.setAzureSharedAccessSignature(getSasToken());
            storageAccount.setSignatureConnection(sasConn);
        } else {
            AzureStorageConnectionAccount accountConn = new AzureStorageConnectionAccount();
            accountConn.setAccountName(ACCOUNT_NAME);
            accountConn.setAccountKey(ACCOUNT_KEY);
            accountConn.setProtocol(Protocol.HTTPS);
            storageAccount.setAccountConnection(accountConn);
        }
        return storageAccount;
    }

    /**
     * Create a default input configuration instance
     */
    protected AzureEventHubsStreamInputConfiguration createInputConfiguration(boolean useSAS) {
        AzureEventHubsStreamInputConfiguration inputConfiguration = new AzureEventHubsStreamInputConfiguration();
        inputConfiguration.setDataset(createDataSet());
        CheckpointStoreConfiguration checkpointStore = getAzureStorageConnection(useSAS);
        inputConfiguration.setCheckpointStore(checkpointStore);
        return inputConfiguration;
    }

    /**
     * Create a default input configuration instance
     */
    protected AzureEventHubsOutputConfiguration createOutputConfiguration() {
        AzureEventHubsOutputConfiguration outputConfiguration = new AzureEventHubsOutputConfiguration();
        outputConfiguration.setDataset(createDataSet());
        return outputConfiguration;
    }

    /**
     * Create a AzureEventHubsDataSet instance
     */
    protected abstract AzureEventHubsDataSet createDataSet();

    /**
     * Create a test records (index is used to generate event sequence )
     */
    protected abstract List<Record> generateSampleData(int index);

}