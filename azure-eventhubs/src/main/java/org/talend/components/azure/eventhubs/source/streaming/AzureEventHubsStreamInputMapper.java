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
package org.talend.components.azure.eventhubs.source.streaming;

import static java.util.Collections.singletonList;

import java.io.Serializable;
import java.util.List;

import javax.json.JsonBuilderFactory;
import javax.json.JsonReaderFactory;
import javax.json.bind.Jsonb;
import javax.json.spi.JsonProvider;

import org.talend.components.azure.eventhubs.service.Messages;
import org.talend.components.azure.eventhubs.source.AzureEventHubsSource;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Assessor;
import org.talend.sdk.component.api.input.Emitter;
import org.talend.sdk.component.api.input.PartitionMapper;
import org.talend.sdk.component.api.input.PartitionSize;
import org.talend.sdk.component.api.input.Split;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

@Version(1)
@Icon(value = Icon.IconType.AZURE_EVENT_HUBS)
@PartitionMapper(name = "AzureEventHubsInputStream", infinite = true)
@Documentation("Mapper to consume mesage from eventhubs")
public class AzureEventHubsStreamInputMapper implements Serializable {

    private final AzureEventHubsStreamInputConfiguration configuration;

    private final RecordBuilderFactory recordBuilderFactory;

    private final JsonBuilderFactory jsonBuilderFactory;

    private final JsonProvider jsonProvider;

    private final JsonReaderFactory readerFactory;

    private final Jsonb jsonb;

    private final Messages messages;

    public AzureEventHubsStreamInputMapper(@Option("configuration") final AzureEventHubsStreamInputConfiguration configuration,
            RecordBuilderFactory recordBuilderFactory, JsonBuilderFactory jsonBuilderFactory, JsonProvider jsonProvider,
            JsonReaderFactory readerFactory, Jsonb jsonb, Messages messages) {
        this.configuration = configuration;
        this.recordBuilderFactory = recordBuilderFactory;
        this.jsonBuilderFactory = jsonBuilderFactory;
        this.jsonProvider = jsonProvider;
        this.readerFactory = readerFactory;
        this.jsonb = jsonb;
        this.messages = messages;
    }

    @Assessor
    public long estimateSize() {
        return 1L;
    }

    @Split
    public List<AzureEventHubsStreamInputMapper> split(@PartitionSize final long bundles) {
        return singletonList(this);
    }

    @Emitter
    public AzureEventHubsSource createWorker() {
        if (configuration.isSampling()) {
            return new AzureEventHubsSamplingSource(configuration, recordBuilderFactory, jsonBuilderFactory, jsonProvider,
                    readerFactory, jsonb, messages);
        } else {
            return new AzureEventHubsUnboundedSource(configuration, recordBuilderFactory, jsonBuilderFactory, jsonProvider,
                    readerFactory, jsonb, messages);
        }
    }
}