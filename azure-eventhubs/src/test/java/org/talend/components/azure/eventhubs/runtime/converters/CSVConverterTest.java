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

import java.io.IOException;
import java.util.List;

import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.talend.components.azure.eventhubs.dataset.AzureEventHubsDataSet;
import org.talend.components.azure.eventhubs.service.Messages;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit5.WithComponents;

import static org.junit.jupiter.api.Assertions.*;

@WithComponents("org.talend.components.azure.eventhubs")
class CSVConverterTest {

    @Service
    public RecordBuilderFactory recordBuilderFactory;

    @Service
    private Messages i18n;

    @Test
    void inferSchema() {
        CSVConverter converter = CSVConverter.of(recordBuilderFactory,
                AzureEventHubsDataSet.FieldDelimiterType.SEMICOLON.getDelimiter(), i18n);
        String value = "1;test1;2017-01-01;1000.2;ant\"ique;";
        Schema schema = converter.inferSchema(value);
        List<Schema.Entry> entries = schema.getEntries();
        assertEquals(6, entries.size());
        assertEquals("field0", entries.get(0).getName());
        assertEquals("field1", entries.get(1).getName());
        assertEquals("field2", entries.get(2).getName());
        assertEquals("field3", entries.get(3).getName());
        assertEquals("field4", entries.get(4).getName());
        assertEquals("field5", entries.get(5).getName());
    }

    @Test
    void toRecord() {
        CSVConverter converter = CSVConverter.of(recordBuilderFactory,
                AzureEventHubsDataSet.FieldDelimiterType.COMMA.getDelimiter(), i18n);
        String value = "1,test1,2017-01-01,1000.2,ant\"ique,";
        Record record = converter.toRecord(value);
        List<Schema.Entry> entries = record.getSchema().getEntries();
        assertEquals(6, entries.size());
        assertEquals("field0", entries.get(0).getName());
        assertEquals("field1", entries.get(1).getName());
        assertEquals("field2", entries.get(2).getName());
        assertEquals("field3", entries.get(3).getName());
        assertEquals("field4", entries.get(4).getName());
        assertEquals("field5", entries.get(5).getName());

        assertEquals("1", record.getString("field0"));
        assertEquals("test1", record.getString("field1"));
        assertEquals("2017-01-01", record.getString("field2"));
        assertEquals("1000.2", record.getString("field3"));
        assertEquals("ant\"ique", record.getString("field4"));
        assertEquals("", record.getString("field5"));

    }

    @Test
    void toRecord4NotSameFieldSize() {
        CSVConverter converter = CSVConverter.of(recordBuilderFactory,
                AzureEventHubsDataSet.FieldDelimiterType.COMMA.getDelimiter(), i18n);
        String value_1 = "1,test1,2017-01-01,1000.2,ant\"ique,";
        // the schema would generated based on value_1
        converter.toRecord(value_1);
        // value_2 have less fields than value_1
        String value_2 = "2,test2,2017-02-01,2000.2,";
        Record record = converter.toRecord(value_2);
        List<Schema.Entry> entries = record.getSchema().getEntries();
        assertEquals(6, entries.size());
        assertEquals("field0", entries.get(0).getName());
        assertEquals("field1", entries.get(1).getName());
        assertEquals("field2", entries.get(2).getName());
        assertEquals("field3", entries.get(3).getName());
        assertEquals("field4", entries.get(4).getName());
        assertEquals("field5", entries.get(5).getName());

        assertEquals("2", record.getString("field0"));
        assertEquals("test2", record.getString("field1"));
        assertEquals("2017-02-01", record.getString("field2"));
        assertEquals("2000.2", record.getString("field3"));
        assertEquals("", record.getString("field4"));
        assertEquals("", record.getString("field5"));

        // more fields than value_1, only keep fields same with value_2
        String value_3 = "1,test1,2017-01-01,1000.2,ant\"ique,ttt6,fff7";
        record = converter.toRecord(value_3);
        assertEquals(6, record.getSchema().getEntries().size());

    }

    @Test
    void fromRecord() {
        CSVConverter converter = CSVConverter.of(recordBuilderFactory,
                AzureEventHubsDataSet.FieldDelimiterType.TAB.getDelimiter(), i18n);
        Record.Builder recordBuilder = recordBuilderFactory.newRecordBuilder();
        recordBuilder.withString("title", "a string sample");
        recordBuilder.withInt("t_int", 710);
        recordBuilder.withLong("t_long", 710L);
        recordBuilder.withDouble("t_double", 71.0);
        recordBuilder.withBoolean("t_boolean", true);

        try {
            String data = converter.fromRecord(recordBuilder.build());
            assertEquals("a string sample\t710\t710\t71.0\ttrue", data);
        } catch (IOException e) {
            fail(e.getMessage());
        }

    }

    @Test
    void getSchema() {

    }
}