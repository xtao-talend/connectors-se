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
package org.talend.components.azure.eventhubs.runtime.converters;

import java.io.Serializable;
import java.io.StringReader;

import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonReaderFactory;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.spi.JsonProvider;

import org.talend.components.azure.eventhubs.service.Messages;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.runtime.record.RecordConverters;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JsonConverter implements RecordConverter<String>, Serializable {

    private Messages messages;

    private final RecordConverters converter = new RecordConverters();

    public RecordBuilderFactory recordBuilderFactory;

    private JsonBuilderFactory jsonBuilderFactory;

    private JsonProvider jsonProvider;

    private JsonReaderFactory readerFactory;

    private Jsonb jsonb;

    private JsonConverter(RecordBuilderFactory recordBuilderFactory, JsonBuilderFactory jsonBuilderFactory,
            JsonProvider jsonProvider, JsonReaderFactory readerFactory, Jsonb jsonb, Messages messages) {
        this.recordBuilderFactory = recordBuilderFactory;
        this.jsonBuilderFactory = jsonBuilderFactory;
        this.jsonProvider = jsonProvider;
        this.readerFactory = readerFactory;
        this.jsonb = jsonb;
        this.messages = messages;
    }

    public static JsonConverter of(RecordBuilderFactory recordBuilderFactory, JsonBuilderFactory jsonBuilderFactory,
            JsonProvider jsonProvider, JsonReaderFactory readerFactory, Jsonb jsonb, Messages messages) {
        return new JsonConverter(recordBuilderFactory, jsonBuilderFactory, jsonProvider, readerFactory, jsonb, messages);
    }

    @Override
    public Schema inferSchema(String value) {
        throw new RuntimeException("not implement");
    }

    @Override
    public Record toRecord(String value) {
        final Record record = converter.toRecord(new RecordConverters.MappingMetaRegistry(),
                readerFactory.createReader(new StringReader(value)).readObject(), () -> jsonb, () -> recordBuilderFactory);
        return record;
    }

    @Override
    public String fromRecord(Record record) throws Exception {
        if (record == null) {
            return null;
        }
        try (final Jsonb jsonb = JsonbBuilder.create()) {
            final JsonObject json = JsonObject.class.cast(converter.toType(new RecordConverters.MappingMetaRegistry(), record,
                    JsonObject.class, () -> jsonBuilderFactory, () -> jsonProvider, () -> jsonb, () -> recordBuilderFactory));
            return json.toString();
        }
    }
}
