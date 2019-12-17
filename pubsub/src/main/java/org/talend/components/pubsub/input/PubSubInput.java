/*
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
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
package org.talend.components.pubsub.input;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.pubsub.v1.PubsubMessage;
import org.talend.components.pubsub.service.I18nMessage;
import org.talend.components.pubsub.service.PubSubService;
import org.talend.sdk.component.api.input.Producer;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class PubSubInput implements MessageReceiver, Serializable {

    protected final PubSubInputConfiguration configuration;

    protected final PubSubService service;

    protected final I18nMessage i18n;

    protected final RecordBuilderFactory builderFactory;

    private final Queue<Record> inbox = new LinkedList<>();

    private Subscriber subscriber;

    public PubSubInput(final PubSubInputConfiguration configuration, final PubSubService service, final I18nMessage i18n,
            final RecordBuilderFactory builderFactory) {
        this.configuration = configuration;
        this.service = service;
        this.i18n = i18n;
        this.builderFactory = builderFactory;
    }

    @PostConstruct
    public void init() {
        subscriber = service.createSubscriber(configuration.getDataSet().getDataStore(), configuration.getDataSet().getTopic(),
                configuration.getDataSet().getSubscription(), this);
        subscriber.startAsync();
    }

    @PreDestroy
    public void release() {
        if (subscriber != null) {
            subscriber.stopAsync();
        }
    }

    @Producer
    public Record next() {
        return inbox.poll();
    }

    @Override
    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {

        Record record = builderFactory.newRecordBuilder().withString("ID", message.getMessageId())
                .withString("content", message.getData().toStringUtf8()).build();
        inbox.offer(record);

        if (configuration.isConsumeMsg()) {
            consumer.ack();
        }
    }
}
