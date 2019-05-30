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

package org.talend.components.azure.eventhubs.dataset;

import org.talend.components.azure.common.connection.AzureStorageConnectionAccount;
import org.talend.components.azure.eventhubs.datastore.AzureEventHubsDataStore;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.action.Validable;
import org.talend.sdk.component.api.configuration.condition.ActiveIf;
import org.talend.sdk.component.api.configuration.constraint.Required;
import org.talend.sdk.component.api.configuration.type.DataSet;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.meta.Documentation;

import lombok.Data;

@Data
@DataSet("AzureEventHubsStreamDataSet")
@GridLayout({ @GridLayout.Row({ "connection" }), @GridLayout.Row({ "eventHubName" }), @GridLayout.Row({ "storageConn" }),
        @GridLayout.Row({ "containerName" }), @GridLayout.Row("valueFormat"), @GridLayout.Row("fieldDelimiter") })
@Documentation("The dataset consume message in eventhubs")
public class AzureEventHubsStreamDataSet implements BaseDataSet {

    @Option
    @Documentation("Connection information to eventhubs")
    private AzureEventHubsDataStore connection;

    @Option
    @Required
    @Validable(value = "checkEventHub", parameters = { "connection", "." })
    @Documentation("The name of the event hub connect to")
    private String eventHubName;

    @Option
    @Documentation("Connection for the Azure Storage account to use for persisting leases and checkpoints.")
    private AzureStorageConnectionAccount storageConn;

    @Option
    @Documentation("Azure Storage container name for use by built-in lease and checkpoint manager.")
    private String containerName;

    @Option
    @ActiveIf(target = ".", value = "-2147483648")
    @Documentation("The format of the records stored in eventhub.")
    private AzureEventHubsDataSet.ValueFormat valueFormat = AzureEventHubsDataSet.ValueFormat.CSV;

    @Option
    @ActiveIf(target = ".", value = "-2147483648")
    @Documentation("The field delimiter of the eventhub message value.")
    private AzureEventHubsDataSet.FieldDelimiterType fieldDelimiter = AzureEventHubsDataSet.FieldDelimiterType.SEMICOLON;

    public enum ValueFormat {
        CSV
    }

    public enum FieldDelimiterType {
        SEMICOLON(";");

        private final String value;

        FieldDelimiterType(final String value) {
            this.value = value;
        }

        public String getDelimiter() {
            return value;
        }
    }

}