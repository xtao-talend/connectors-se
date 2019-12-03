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
 */
package org.talend.components.pubsub.dataset;

import lombok.Data;
import org.talend.components.pubsub.datastore.PubSubDataStore;
import org.talend.components.pubsub.service.PubSubService;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.action.Suggestable;
import org.talend.sdk.component.api.configuration.condition.ActiveIf;
import org.talend.sdk.component.api.configuration.constraint.Required;
import org.talend.sdk.component.api.configuration.type.DataSet;
import org.talend.sdk.component.api.configuration.ui.DefaultValue;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;

@Data
@DataSet("PubSubDataSet")
@GridLayout({ //
        @GridLayout.Row("dataStore"), @GridLayout.Row("topic"), @GridLayout.Row("subscription"), @GridLayout.Row("valueFormat"),
        @GridLayout.Row("fieldDelimiter") })
@Documentation("Pub/Sub Dataset Properties")
public class PubSubDataSet implements Serializable {

    @Option
    @Documentation("Connection")
    private PubSubDataStore dataStore;

    @Option
    @Required
    @Suggestable(value = PubSubService.ACTION_SUGGESTION_TOPICS, parameters = "dataStore")
    @Documentation("Topic")
    private String topic;

    @Option
    @Required
    @Suggestable(value = PubSubService.ACTION_SUGGESTION_SUBSCRIPTIONS, parameters = { "dataStore", "topic" })
    @Documentation("Subscription")
    private String subscription;

    @Option
    @Required
    @DefaultValue(value = "CSV")
    @Documentation("Value format")
    private ValueFormat valueFormat;

    @Option
    @DefaultValue(value = ";")
    @ActiveIf(target = "valueFormat", value = { "CSV" })
    @Required
    @Documentation("Field delimiter")
    private String fieldDelimiter;

    public enum ValueFormat {
        CSV,
        JSON,
        AVRO
    }

}
