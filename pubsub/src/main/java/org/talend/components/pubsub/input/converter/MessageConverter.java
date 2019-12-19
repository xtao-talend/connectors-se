package org.talend.components.pubsub.input.converter;

import com.google.pubsub.v1.PubsubMessage;
import lombok.Setter;
import org.talend.components.pubsub.dataset.PubSubDataSet;
import org.talend.components.pubsub.input.PubSubInputConfiguration;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

public abstract class MessageConverter {

    @Setter
    protected RecordBuilderFactory recordBuilderFactory;

    public abstract void init(PubSubDataSet dataset);

    public abstract boolean acceptFormat(PubSubDataSet.ValueFormat format);

    public abstract Record convertMessage(PubsubMessage message);

    protected final String getMessageContentAsString(PubsubMessage message) {
        return message == null ? "null" : message.getData().toStringUtf8();
    }

    protected final byte[] getMessageContentAsBytes(PubsubMessage message) {
        return message == null ? new byte[]{} : message.getData().toByteArray();
    }
}
