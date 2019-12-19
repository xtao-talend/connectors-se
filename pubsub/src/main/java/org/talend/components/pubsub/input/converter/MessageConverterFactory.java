package org.talend.components.pubsub.input.converter;

import org.talend.components.pubsub.dataset.PubSubDataSet;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import java.util.Arrays;
import java.util.Optional;

public class MessageConverterFactory {

    private static final Class<? extends MessageConverter>[] IMPLEMENTATIONS = new Class[] {
        CSVMessageConverter.class,
        DefaultMessageConverter.class
    };

    public MessageConverter getConverter(PubSubDataSet dataSet, RecordBuilderFactory recordBuilderFactory) {
        PubSubDataSet.ValueFormat format = dataSet.getValueFormat();

        Optional<? extends MessageConverter> opt =
            Arrays.stream(IMPLEMENTATIONS)
                    .map(c -> {
                        try {
                            return c.newInstance();
                        } catch (Exception e) {
                            return null;
                        }})
                    .filter(mc -> mc != null && ((MessageConverter) mc).acceptFormat(format))
                    .findFirst();

        MessageConverter messageConverter = opt.isPresent() ? opt.get() : null;
        if (messageConverter != null) {
            messageConverter.setRecordBuilderFactory(recordBuilderFactory);
            messageConverter.init(dataSet);
        }

        return messageConverter;
    }
}
