package org.talend.components.pubsub.input.converter;

import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.talend.components.pubsub.dataset.PubSubDataSet;
import org.talend.components.pubsub.service.I18nMessage;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.runtime.internationalization.InternationalizationServiceFactory;
import org.talend.sdk.component.runtime.record.RecordBuilderFactoryImpl;

import java.util.Arrays;
import java.util.Locale;

public class CSVMessageConverterTest {

    private CSVMessageConverter beanUnderTest;
    private PubSubDataSet dataSet;

    @BeforeEach
    public void init() {
        beanUnderTest = new CSVMessageConverter();
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
                format == PubSubDataSet.ValueFormat.CSV,
                beanUnderTest.acceptFormat(format), "CVSMessageConverter must accept only CSV");
    }

    @Test
    public void convertTest() {

        dataSet.setValueFormat(PubSubDataSet.ValueFormat.CSV);
        dataSet.setFieldDelimiter(";");
        beanUnderTest.init(dataSet);

        PubsubMessage message = PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8("1;John Smith;US;1.12356"))
                .build();

        Record record = beanUnderTest.convertMessage(message);
        Assertions.assertNotNull(record, "Record is null");
        Assertions.assertEquals("1", record.getString(CSVMessageConverter.FIELD_PREFIX + "0"));
        Assertions.assertEquals("John Smith", record.getString(CSVMessageConverter.FIELD_PREFIX + "1"));
        Assertions.assertEquals("US", record.getString(CSVMessageConverter.FIELD_PREFIX + "2"));
        Assertions.assertEquals("1.12356", record.getString(CSVMessageConverter.FIELD_PREFIX + "3"));
        Assertions.assertNotNull(record.getSchema(), "Schema must not be null");
    }

}
