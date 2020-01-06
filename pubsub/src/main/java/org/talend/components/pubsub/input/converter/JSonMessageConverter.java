package org.talend.components.pubsub.input.converter;

import com.google.pubsub.v1.PubsubMessage;
import org.talend.components.pubsub.dataset.PubSubDataSet;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.stream.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collection;

public class JSonMessageConverter extends MessageConverter {


    @Override
    public void init(PubSubDataSet dataset) {

    }

    @Override
    public boolean acceptFormat(PubSubDataSet.ValueFormat format) {
        return format == PubSubDataSet.ValueFormat.JSON;
    }

    @Override
    public Record convertMessage(PubsubMessage message) {
        InputStream in = new ByteArrayInputStream(getMessageContentAsBytes(message));
        JsonParser parser = Json.createParser(in);
        JsonObject jsonObject = parser.getObject();

        return toRecord(jsonObject);
    }

    private Record toRecord(JsonObject jsonObject) {
        Schema schema = guessSchema(jsonObject);
        Record.Builder recordBuilder = getRecordBuilderFactory().newRecordBuilder(schema);
        jsonObject.entrySet().stream().forEach(e -> fillEntry(e.getKey(), e.getValue(), recordBuilder));
        return recordBuilder.build();
    }

    private Collection toCollection(JsonArray array) {
        return null;
        // TODO
    }

    private void fillEntry(String fieldName, JsonValue value, Record.Builder recordBuilder) {
        JsonValue.ValueType valueType = value.getValueType();
        switch (valueType) {
            case ARRAY:
                JsonArray array = (JsonArray) value;
                recordBuilder.withArray(
                        getRecordBuilderFactory().newEntryBuilder()
                                .withName(fieldName)
                                .withType(getTypeFor(array.get(0).getValueType()))
                                .build(),
                        toCollection(array));
                break;
            case STRING:
                recordBuilder.withString(fieldName, value.toString());
            case TRUE:
                recordBuilder.withBoolean(fieldName, true);
                break;
            case FALSE:
                recordBuilder.withBoolean(fieldName, false);
                break;
            case NUMBER:
                recordBuilder.withDouble(fieldName, Double.parseDouble(value.toString()));
                break;
            case OBJECT:
                recordBuilder.withRecord(fieldName, toRecord((JsonObject) value));
                break;
        }
    }

    private Schema guessSchema(JsonObject jsonObject) {
        Schema.Builder schemaBuilder=  getRecordBuilderFactory().newSchemaBuilder(Schema.Type.RECORD);

        jsonObject.entrySet().stream().forEach(e ->
            schemaBuilder.withEntry(getRecordBuilderFactory().newEntryBuilder()
                .withName(e.getKey())
                .withType(getTypeFor(e.getValue().getValueType()))
                .withNullable(true)
                .withElementSchema(getElementSchema(e.getValue()))
                .build()));

        return schemaBuilder.build();
    }

    private Schema getElementSchema(JsonValue value) {
        JsonValue.ValueType valueType = value.getValueType();

        if (valueType == JsonValue.ValueType.ARRAY) {
            JsonArray array = (JsonArray) value;
            JsonValue item = array.get(0);
            if (item != null) {
                if (item.getValueType() == JsonValue.ValueType.OBJECT) {
                    return getElementSchema(item);
                } else {
                    return getRecordBuilderFactory().newSchemaBuilder(getTypeFor(item.getValueType())).build();
                }
            }
        } else if (valueType == JsonValue.ValueType.OBJECT) {
            JsonObject object = (JsonObject) value;
            if (object != null) {
                return guessSchema(object);
            }
        }

        return null;
    }

    private Schema.Type getTypeFor(JsonValue.ValueType valueType) {
        switch (valueType) {
            case ARRAY: return Schema.Type.ARRAY;
            case STRING:return Schema.Type.STRING;
            case TRUE: case FALSE:return Schema.Type.BOOLEAN;
            case NUMBER: return Schema.Type.DOUBLE;
            case OBJECT: return Schema.Type.RECORD;
            case NULL: return Schema.Type.STRING;
        }
        throw new RuntimeException(getI18nMessage().errorJsonType(valueType.toString()));
    }
}
