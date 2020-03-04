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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit5.WithComponents;

@WithComponents("org.talend.components.azure.eventhubs")
class AvroConverterTest {

    private AvroConverter converter;

    private GenericRecord avroRecord;

    @Service
    public RecordBuilderFactory recordBuilderFactory;

    @BeforeEach
    protected void setUp() {
        converter = AvroConverter.of(recordBuilderFactory);
        avroRecord = new GenericData.Record( //
                SchemaBuilder.builder().record("avro_sample").fields() //
                        .name("title").type().stringType().noDefault() //
                        .name("t_int").type().intType().noDefault() //
                        .name("t_long").type().longType().noDefault() //
                        .name("t_double").type().doubleType().noDefault() //
                        .name("t_boolean").type().booleanType().noDefault() //
                        .endRecord());
        avroRecord.put("title", "a string sample");
        avroRecord.put("t_int", 710);
        avroRecord.put("t_long", 710L);
        avroRecord.put("t_double", 71.0);
        avroRecord.put("t_boolean", true);
    }

    @Test
    void inferSchema() {
        Schema schema = converter.inferSchema(avroRecord);
        List<Schema.Entry> entries = schema.getEntries();
        assertEquals(5, entries.size());
        assertEquals("title", entries.get(0).getName());
        assertEquals("t_int", entries.get(1).getName());
        assertEquals("t_long", entries.get(2).getName());
        assertEquals("t_double", entries.get(3).getName());
        assertEquals("t_boolean", entries.get(4).getName());
    }

    @Test
    void toRecord() {
        Record record = converter.toRecord(avroRecord);

        assertNotNull(record);
        assertEquals("a string sample", record.getString("title"));
        assertEquals(710, record.getInt("t_int"));
        assertEquals(710L, record.getLong("t_long"));
        assertEquals(71.0, record.getDouble("t_double"));
        assertTrue(record.getBoolean("t_boolean"));
    }

    @Test
    void fromRecord() {
        Record.Builder recordBuilder = recordBuilderFactory.newRecordBuilder();
        recordBuilder.withString("title", "a string sample");
        recordBuilder.withInt("t_int", 710);
        recordBuilder.withLong("t_long", 710L);
        recordBuilder.withDouble("t_double", 71.0);
        recordBuilder.withBoolean("t_boolean", true);
        try {
            GenericRecord genericRecord = converter.fromRecord(recordBuilder.build());
            assertNotNull(genericRecord);
            assertEquals("a string sample", genericRecord.get("title"));
            assertEquals(710, genericRecord.get("t_int"));
            assertEquals(710L, genericRecord.get("t_long"));
            assertEquals(71.0, genericRecord.get("t_double"));
            assertTrue((Boolean) genericRecord.get("t_boolean"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}