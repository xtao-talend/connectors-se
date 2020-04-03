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

import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_CHARSET;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import org.talend.components.azure.eventhubs.service.Messages;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CSVConverter implements RecordConverter<String>, Serializable {

    public static final String FIELD_PREFIX = "field";

    public static final String FIELD_DEFAULT_VALUE = "";

    private String fieldDelimiter;

    @Getter
    private Schema schema;

    public RecordBuilderFactory recordBuilderFactory;

    private Messages messages;

    private CSVConverter(RecordBuilderFactory recordBuilderFactory, String fieldDelimiter, Messages messages) {
        this.recordBuilderFactory = recordBuilderFactory;
        this.fieldDelimiter = fieldDelimiter;
        this.messages = messages;
    }

    public static CSVConverter of(RecordBuilderFactory recordBuilderFactory, String fieldDelimiter, Messages messages) {
        return new CSVConverter(recordBuilderFactory, fieldDelimiter, messages);
    }

    @Override
    public Schema inferSchema(String value) {
        Schema.Builder builder = recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD);
        Set<String> existNames = new HashSet<>();
        int index = 0;
        String[] fieldValues = value.split(fieldDelimiter, -1);
        for (int i = 0; i < fieldValues.length; i++) {
            Schema.Entry.Builder entryBuilder = recordBuilderFactory.newEntryBuilder();
            String fieldName = FIELD_PREFIX + i;

            String finalName = SchemaUtils.correct(fieldName, index++, existNames);
            existNames.add(finalName);
            builder.withEntry(entryBuilder.withName(finalName).withType(Schema.Type.STRING).build());
        }
        return builder.build();
    }

    @Override
    public Record toRecord(String value) {
        if (value == null) {
            return null;
        }
        if (schema == null) {
            schema = inferSchema(value);
        }
        String[] fieldValues = value.split(fieldDelimiter, -1);
        Record.Builder recordBuilder = recordBuilderFactory.newRecordBuilder(schema);
        for (int i = 0; i < schema.getEntries().size(); i++) {
            if (i < fieldValues.length) {
                recordBuilder.withString(schema.getEntries().get(i), fieldValues[i]);
            } else {
                recordBuilder.withString(schema.getEntries().get(i), FIELD_DEFAULT_VALUE);
            }
        }
        return recordBuilder.build();
    }

    @Override
    public String fromRecord(Record record) throws IOException {
        if (record == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        Schema schema = record.getSchema();
        boolean isFirstColumn = true;
        for (Schema.Entry field : schema.getEntries()) {
            if (!isFirstColumn) {
                sb.append(fieldDelimiter);
            } else {
                isFirstColumn = false;
            }
            sb.append(getStringValue(record, field));

        }
        return sb.toString();
    }

    private String getStringValue(Record record, Schema.Entry field) throws IOException {
        Object value = null;
        switch (field.getType()) {
        case STRING:
            value = record.getString(field.getName());
            break;
        case BOOLEAN:
            if (record.getOptionalBoolean(field.getName()).isPresent()) {
                value = record.getBoolean(field.getName());
            }
            break;
        case DOUBLE:
            if (record.getOptionalDouble(field.getName()).isPresent()) {
                value = record.getDouble(field.getName());
            }
            break;
        case FLOAT:
            if (record.getOptionalFloat(field.getName()).isPresent()) {
                value = record.getFloat(field.getName());
            }
            break;
        case LONG:
            if (record.getOptionalLong(field.getName()).isPresent()) {
                value = record.getLong(field.getName());
            }
            break;
        case INT:
            if (record.getOptionalInt(field.getName()).isPresent()) {
                value = record.getInt(field.getName());
            }
            break;
        case DATETIME:
            value = record.getDateTime(field.getName());
            break;
        case BYTES:
            if (record.getOptionalBytes(field.getName()).isPresent()) {
                value = new String(record.getBytes(field.getName()), DEFAULT_CHARSET);
            }
            break;
        default:
            throw new IllegalStateException(messages.errorUnsupportedType(field.getType().name(), field.getName()));
        }
        return value == null ? "" : String.valueOf(value);

    }
}
