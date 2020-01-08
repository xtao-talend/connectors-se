package org.talend.components.pubsub.input.converter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.talend.components.pubsub.dataset.PubSubDataSet;
import org.talend.components.pubsub.service.I18nMessage;
import org.talend.sdk.component.runtime.internationalization.InternationalizationServiceFactory;
import org.talend.sdk.component.runtime.record.RecordBuilderFactoryImpl;

import java.util.Arrays;
import java.util.Locale;

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
}
