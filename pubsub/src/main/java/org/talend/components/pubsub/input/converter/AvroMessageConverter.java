package org.talend.components.pubsub.input.converter;

import com.google.pubsub.v1.PubsubMessage;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;
import org.talend.components.pubsub.dataset.PubSubDataSet;
import org.talend.sdk.component.api.record.Record;

public class AvroMessageConverter extends MessageConverter {

    private BinaryDecoder decoder;


    @Override
    public void init(PubSubDataSet dataset) {

    }

    @Override
    public boolean acceptFormat(PubSubDataSet.ValueFormat format) {
        return format == PubSubDataSet.ValueFormat.AVRO;
    }

    @Override
    public Record convertMessage(PubsubMessage message) {
        decoder = DecoderFactory.get().binaryDecoder(getMessageContentAsBytes(message), decoder);
        //DatumReader<Record> recordDatumReader = new RecordDatumReader();

        return null;
    }
}
