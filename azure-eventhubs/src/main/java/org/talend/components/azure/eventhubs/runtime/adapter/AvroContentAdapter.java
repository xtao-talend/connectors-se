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

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.generic.IndexedRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.talend.components.azure.eventhubs.dataset.AzureEventHubsDataSet;
import org.talend.components.azure.eventhubs.runtime.converters.AvroConverter;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

public class AvroContentAdapter implements EventDataContentAdapter {

    private transient GenericDatumWriter<Object> datumWriter;

    private transient GenericDatumReader<GenericRecord> datumReader;

    private final AvroConverter recordConverter;

    private final AzureEventHubsDataSet dataset;

    public AvroContentAdapter(AzureEventHubsDataSet dataset, RecordBuilderFactory recordBuilderFactory) {
        recordConverter = AvroConverter.of(recordBuilderFactory);
        this.dataset = dataset;

    }

    @Override
    public Record toRecord(byte[] event) throws IOException {
        if (datumReader == null) {
            org.apache.avro.Schema.Parser parser = new org.apache.avro.Schema.Parser();
            Schema schema = parser.parse(dataset.getAvroSchema());
            datumReader = new GenericDatumReader<GenericRecord>(schema);
        }
        BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(event, null);
        GenericRecord genericRecord = datumReader.read(null, decoder);
        return recordConverter.toRecord(genericRecord);
    }

    @Override
    public byte[] toBytes(Record record) throws IOException {
        if (datumWriter == null) {
            org.apache.avro.Schema.Parser parser = new org.apache.avro.Schema.Parser();
            Schema schema = parser.parse(dataset.getAvroSchema());
            datumWriter = new GenericDatumWriter<>(schema);
        }
        IndexedRecord indexedRecord = recordConverter.fromRecord(record);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
        datumWriter.write(indexedRecord, encoder);
        encoder.flush();
        byte[] payloadBytes = out.toByteArray();
        out.close();
        return payloadBytes;
    }
}
