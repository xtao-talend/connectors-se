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
package org.talend.components.azure.eventhubs.runtime.adapter;

import java.io.Serializable;

import javax.json.JsonBuilderFactory;
import javax.json.JsonReaderFactory;
import javax.json.bind.Jsonb;
import javax.json.spi.JsonProvider;

import org.talend.components.azure.eventhubs.dataset.AzureEventHubsDataSet;
import org.talend.components.azure.eventhubs.service.Messages;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

public class ContentAdapterFactory implements Serializable {

    public static EventDataContentAdapter getAdapter(AzureEventHubsDataSet dataset, RecordBuilderFactory recordBuilderFactory,
            JsonBuilderFactory jsonBuilderFactory, JsonProvider jsonProvider, JsonReaderFactory readerFactory, Jsonb jsonb,
            Messages messages) {

        switch (dataset.getValueFormat()) {
        case CSV:
            return new CsvContentAdapter(dataset, recordBuilderFactory, messages);
        case AVRO:
            return new AvroContentAdapter(dataset, recordBuilderFactory);
        case JSON:
            return new JsonContentAdapter(recordBuilderFactory, jsonBuilderFactory, jsonProvider, readerFactory, jsonb, messages);
        case TEXT:
            return new TextContentAdapter(recordBuilderFactory, messages);
        default:
            throw new IllegalArgumentException("To be implemented: " + dataset.getValueFormat());
        }
    }
}
