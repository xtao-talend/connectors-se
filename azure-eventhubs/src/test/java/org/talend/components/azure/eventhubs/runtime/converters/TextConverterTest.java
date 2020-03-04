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

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.talend.components.azure.eventhubs.service.Messages;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit5.WithComponents;

import static org.junit.jupiter.api.Assertions.*;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.PAYLOAD_COLUMN;

@WithComponents("org.talend.components.azure.eventhubs")
class TextConverterTest {

    private TextConverter converter;

    @Service
    public RecordBuilderFactory recordBuilderFactory;

    @Service
    private Messages i18n;

    @BeforeEach
    protected void setUp() {
        converter = TextConverter.of(recordBuilderFactory, i18n);
    }

    @Test
    void inferSchema() {
        Schema schema = converter.inferSchema("message");
        List<Schema.Entry> entries = schema.getEntries();
        assertEquals(1, entries.size());
        assertEquals(PAYLOAD_COLUMN, entries.get(0).getName());
    }

    @Test
    void toRecord() {
        String message = "message: please convert me!";
        Record record = converter.toRecord(message);
        assertNotNull(record);
        List<Schema.Entry> entries = record.getSchema().getEntries();
        assertEquals(1, entries.size());
        assertEquals(PAYLOAD_COLUMN, entries.get(0).getName());

        assertEquals(message, record.getString(PAYLOAD_COLUMN));
    }

    @Test
    void fromRecordFailed() {
        Record.Builder recordBuilder = recordBuilderFactory.newRecordBuilder();
        recordBuilder.withString("ID", "No.00001");
        recordBuilder.withString("NAME", "Josh");
        recordBuilder.withInt("AGE", 32);
        RuntimeException re = Assertions.assertThrows(RuntimeException.class, () -> converter.fromRecord(recordBuilder.build()));
        assertEquals(i18n.errorMissingElement(PAYLOAD_COLUMN), re.getMessage());
    }

    @Test
    void fromRecord() {
        String dataContent = "here is a azure eventhubs message!";
        Record.Builder recordBuilder = recordBuilderFactory.newRecordBuilder();
        recordBuilder.withString(PAYLOAD_COLUMN, dataContent);
        String eventData = converter.fromRecord(recordBuilder.build());
        assertNotNull(eventData);
        assertEquals(dataContent, eventData);
    }
}