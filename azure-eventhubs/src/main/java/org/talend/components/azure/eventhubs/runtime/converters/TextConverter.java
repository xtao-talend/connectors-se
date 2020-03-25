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

import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.PAYLOAD_COLUMN;

import java.io.Serializable;

import org.talend.components.azure.eventhubs.service.Messages;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TextConverter implements RecordConverter<String>, Serializable {

    private Messages messages;

    @Getter
    private Schema schema;

    public RecordBuilderFactory recordBuilderFactory;

    private TextConverter(RecordBuilderFactory recordBuilderFactory, Messages messages) {
        this.recordBuilderFactory = recordBuilderFactory;
        this.messages = messages;
    }

    public static TextConverter of(RecordBuilderFactory recordBuilderFactory, Messages messages) {
        return new TextConverter(recordBuilderFactory, messages);
    }

    @Override
    public Schema inferSchema(String value) {
        Schema.Builder builder = recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD);
        Schema.Entry.Builder entryBuilder = recordBuilderFactory.newEntryBuilder();
        builder.withEntry(entryBuilder.withName(PAYLOAD_COLUMN).withType(Schema.Type.STRING).build());
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
        Record.Builder recordBuilder = recordBuilderFactory.newRecordBuilder(schema);
        recordBuilder.withString(PAYLOAD_COLUMN, value);
        return recordBuilder.build();
    }

    @Override
    public String fromRecord(Record record) throws RuntimeException {
        if (record == null) {
            return null;
        }
        Schema schema = record.getSchema();
        boolean existEventElement = false;
        for (Schema.Entry entry : schema.getEntries()) {
            if (PAYLOAD_COLUMN.equals(entry.getName())) {
                existEventElement = true;
                break;
            }
        }
        if (!existEventElement) {
            throw new RuntimeException(messages.errorMissingElement(PAYLOAD_COLUMN));
        }
        return record.getString(PAYLOAD_COLUMN);
    }
}
