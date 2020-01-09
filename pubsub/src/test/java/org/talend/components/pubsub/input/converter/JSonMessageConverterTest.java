package org.talend.components.pubsub.input.converter;

import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.talend.components.pubsub.dataset.PubSubDataSet;
import org.talend.components.pubsub.service.I18nMessage;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.runtime.internationalization.InternationalizationServiceFactory;
import org.talend.sdk.component.runtime.record.RecordBuilderFactoryImpl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;

@Slf4j
public class JSonMessageConverterTest {

    private JSonMessageConverter beanUnderTest;
    private PubSubDataSet dataSet;

    @BeforeEach
    public void init() {
        beanUnderTest = new JSonMessageConverter();
        beanUnderTest.setI18nMessage(
                new InternationalizationServiceFactory(() -> Locale.US)
                        .create(I18nMessage.class, Thread.currentThread().getContextClassLoader()));
        beanUnderTest.setRecordBuilderFactory(new RecordBuilderFactoryImpl(null));

        dataSet = new PubSubDataSet();
    }

    @Test
    public void testFormats() {
        Arrays.stream(PubSubDataSet.ValueFormat.values()).forEach(this::testFormat);
    }

    private void testFormat(PubSubDataSet.ValueFormat format) {
        dataSet.setValueFormat(format);
        beanUnderTest.init(dataSet);
        Assertions.assertEquals(
                format == PubSubDataSet.ValueFormat.JSON,
                beanUnderTest.acceptFormat(format), "JSonMessageConverter must accept only JSON");
    }

    @Test
    public void convertTest() throws IOException {

        dataSet.setValueFormat(PubSubDataSet.ValueFormat.JSON);
        beanUnderTest.init(dataSet);

        ByteArrayOutputStream jsonBytes = new ByteArrayOutputStream();
        try (InputStream jsonStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("json/sample.json")) {
            byte[] buffer = new byte[1024];
            int read = 0;
            while ((read = jsonStream.read(buffer)) > 0) {
                jsonBytes.write(buffer, 0, read);
            }
        }
        jsonBytes.flush();
        jsonBytes.close();

        PubsubMessage message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFrom(jsonBytes.toByteArray()))
                .build();

        Record record = beanUnderTest.convertMessage(message);



        Assertions.assertNotNull(record, "Record is null.");
        Assertions.assertNotNull(record.getSchema(), "Record schema is null.");
        Assertions.assertNotNull(record.getArray(String.class, "innerArray"));


    }
}
