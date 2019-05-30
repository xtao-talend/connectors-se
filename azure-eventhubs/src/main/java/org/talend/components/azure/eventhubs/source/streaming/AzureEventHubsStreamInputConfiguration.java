/*
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package org.talend.components.azure.eventhubs.source.streaming;

import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_CONSUMER_GROUP;

import java.io.Serializable;

import org.talend.components.azure.common.connection.AzureStorageConnectionAccount;
import org.talend.components.azure.eventhubs.dataset.AzureEventHubsDataSet;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.meta.Documentation;

import lombok.Data;

@Data
@GridLayout({ @GridLayout.Row({ "dataset" }), @GridLayout.Row({ "consumerGroupName" }), @GridLayout.Row({ "storageConn" }),
        @GridLayout.Row({ "containerName" }), @GridLayout.Row({ "commitOffsetEvery" }) })
@Documentation("Consume message from eventhubs configuration")
public class AzureEventHubsStreamInputConfiguration implements Serializable {

    @Option
    @Documentation("The dataset to consume")
    private AzureEventHubsDataSet dataset;

    @Option
    @Documentation("The consumer group name that this receiver should be grouped under")
    private String consumerGroupName = DEFAULT_CONSUMER_GROUP;

    @Option
    @Documentation("Connection for the Azure Storage account to use for persisting leases and checkpoints.")
    private AzureStorageConnectionAccount storageConn;

    @Option
    @Documentation("Azure Storage container name for use by built-in lease and checkpoint manager.")
    private String containerName;

    @Option
    @Documentation("How frequently checkpointing")
    private int commitOffsetEvery = 5;

}