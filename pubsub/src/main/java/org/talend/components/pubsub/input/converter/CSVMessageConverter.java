package org.talend.components.pubsub.input.converter;

import com.google.pubsub.v1.PubsubMessage;
import org.apache.avro.SchemaBuilder;
import org.talend.components.pubsub.dataset.PubSubDataSet;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;

import java.util.stream.IntStream;

public class CSVMessageConverter extends MessageConverter {

    public static final String FIELD_PREFIX = "field";
    public static final String FIELD_DEFAULT_VALUE = "";


    private String delimiter;


    @Override
    public void init(PubSubDataSet dataset) {
        this.delimiter = dataset.getFieldDelimiter();
    }

    @Override
    public boolean acceptFormat(PubSubDataSet.ValueFormat format) {
        return format == PubSubDataSet.ValueFormat.CSV;
    }

    @Override
    public Record convertMessage(PubsubMessage message) {
        String messageContent = getMessageContentAsString(message);
        String[] parts = messageContent.split(delimiter, -1);

        int nbFields = parts.length;
        Schema.Builder schemaBuilder = recordBuilderFactory.newSchemaBuilder(Schema.Type.RECORD);
        IntStream.range(0, nbFields)
                .mapToObj(i -> FIELD_PREFIX + i )
                .forEach(f -> schemaBuilder.withEntry(recordBuilderFactory.newEntryBuilder()
                        .withName(f)
                        .withType(Schema.Type.STRING)
                        .withNullable(true)
                        .withDefaultValue(FIELD_DEFAULT_VALUE)
                        .build()));

        Record.Builder recordBuilder = recordBuilderFactory.newRecordBuilder(schemaBuilder.build());
        IntStream.range(0, nbFields)
                .forEach(i -> recordBuilder.withString(FIELD_PREFIX + i, parts[i]));
        return recordBuilder.build();
    }
}
