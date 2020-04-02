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

import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_CONSUMER_GROUP;

import java.io.Serializable;

import org.talend.components.azure.eventhubs.dataset.AzureEventHubsDataSet;
import org.talend.components.extension.Reference;
import org.talend.components.extension.Reference.Bindings;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.condition.ActiveIf;
import org.talend.sdk.component.api.configuration.constraint.Min;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.meta.Documentation;

import lombok.Data;

@Data
@GridLayout({ @GridLayout.Row({ "dataset" }), //
        @GridLayout.Row({ "consumerGroupName" }), //
        @GridLayout.Row({ "autoOffsetReset" }), //
        @GridLayout.Row({ "sequenceNum", "inclusiveFlag" }), //
        @GridLayout.Row({ "enqueuedDateTime" }), //
        @GridLayout.Row({ "checkpointStore" }), //
        @GridLayout.Row({ "containerName" }), //
        @GridLayout.Row({ "commitOffsetEvery" }) })

@Documentation("Consume message from eventhubs configuration")
public class AzureEventHubsStreamInputConfiguration implements Serializable {

    @Option
    @Documentation("The dataset to consume")
    private AzureEventHubsDataSet dataset;

    @Option
    @Documentation("The consumer group name that this receiver should be grouped under")
    private String consumerGroupName = DEFAULT_CONSUMER_GROUP;

    @Option
    @Documentation("If offsets don't already exist, where to start reading in the topic.")
    private OffsetResetStrategy autoOffsetReset = OffsetResetStrategy.LATEST;

    @Option
    @Min(-1)
    @ActiveIf(target = "autoOffsetReset", value = "SEQUENCE")
    @Documentation("The sequence number of the event")
    private Long sequenceNum = -1L;

    @Option
    @ActiveIf(target = "autoOffsetReset", value = "SEQUENCE")
    @Documentation("Will include the specified event when set to true; otherwise, the next event is returned")
    private boolean inclusiveFlag = true;

    @Option
    @ActiveIf(target = "autoOffsetReset", value = "DATETIME")
    @Documentation("DateTime is the enqueued time of the event")
    private String enqueuedDateTime;

    @Option
    @Reference(configurationId = "YXp1cmVibG9iI0F6dXJlI2RhdGFzdG9yZSNkZWZhdWx0", bindings = {
            @Bindings(from = "configuration.useAzureSharedSignature", to = "configuration.checkpointStore.useAzureSharedSignature"),
            @Bindings(from = "configuration.signatureConnection.azureSharedAccessSignature", to = "configuration.checkpointStore.storageConnectionSignature.azureSharedAccessSignature"),
            @Bindings(from = "configuration.endpointSuffix", to = "configuration.checkpointStore.endpointSuffix"),
            @Bindings(from = "configuration.accountConnection.accountName", to = "configuration.checkpointStore.accountConnection.accountName"),
            @Bindings(from = "configuration.accountConnection.accountKey", to = "configuration.checkpointStore.accountConnection.accountKey"),
            @Bindings(from = "configuration.accountConnection.protocol", to = "configuration.checkpointStore.accountConnection.protocol") })
    @Documentation("Connection for the Azure Storage account to use for persisting leases and checkpoints.")
    private CheckpointStoreConfiguration checkpointStore;

    @Option
    @Documentation("Azure Storage container name for use by built-in lease and checkpoint manager.")
    private String containerName;

    @Option
    @Min(1)
    @Documentation("How frequently checkpointing")
    private int commitOffsetEvery = 5;

    // for sampling
    @Documentation("Whether this is use for sampling")
    private boolean sampling;

    public enum OffsetResetStrategy {
        LATEST,
        EARLIEST,
        SEQUENCE,
        DATETIME
    }

}