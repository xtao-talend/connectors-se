package org.talend.components.pubsub.input.converter;

import com.google.pubsub.v1.PubsubMessage;
import org.talend.components.pubsub.dataset.PubSubDataSet;
import org.talend.sdk.component.api.record.Record;

/**
 * {@link MessageConverter} that accepts any message.
 */
public class DefaultMessageConverter extends MessageConverter {

    @Override
    public void init(PubSubDataSet dataset) {

    }

    @Override
    public boolean acceptFormat(PubSubDataSet.ValueFormat format) {
        return true;
    }

    @Override
    public Record convertMessage(PubsubMessage message) {
        return recordBuilderFactory.newRecordBuilder()
                .withString("ID", message.getMessageId())
                .withString("content", getMessageContentAsString(message))
                .build();
    }
}
