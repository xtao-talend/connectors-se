package org.talend.components.pubsub.input;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.impl.StaticLoggerBinder;
import org.talend.components.pubsub.PubSubTestUtil;
import org.talend.components.pubsub.dataset.PubSubDataSet;
import org.talend.components.pubsub.datastore.PubSubDataStore;
import org.talend.components.pubsub.service.PubSubService;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.junit.BaseComponentsHandler;
import org.talend.sdk.component.junit.SimpleFactory;
import org.talend.sdk.component.junit.environment.Environment;
import org.talend.sdk.component.junit.environment.EnvironmentConfiguration;
import org.talend.sdk.component.junit.environment.builtin.ContextualEnvironment;
import org.talend.sdk.component.junit.environment.builtin.beam.SparkRunnerEnvironment;
import org.talend.sdk.component.junit5.Injected;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.junit5.environment.EnvironmentalTest;
import org.talend.sdk.component.runtime.manager.chain.Job;

import java.util.List;

@Slf4j
@Environment(ContextualEnvironment.class)
@EnvironmentConfiguration(environment = "Contextual", systemProperties = {})
@WithComponents(value = "org.talend.components.pubsub")
@Tag("IT")
public class PubSubInputTest {

    @Service
    protected PubSubService service;

    @Injected
    private BaseComponentsHandler componentsHandler;

    private PubSubInputConfiguration configuration;

    @BeforeEach
    void buildConfig()  {

        final StaticLoggerBinder binder = StaticLoggerBinder.getSingleton();

        componentsHandler.injectServices(this);

        PubSubDataStore dataStore = PubSubTestUtil.getDataStore();

        PubSubDataSet dataset = new PubSubDataSet();
        dataset.setDataStore(dataStore);
        dataset.setTopic("rlecomteTopic");
        dataset.setSubscription("ITSub");
        dataset.setValueFormat(PubSubDataSet.ValueFormat.CSV);
        dataset.setFieldDelimiter(";");

        configuration = new PubSubInputConfiguration();
        configuration.setDataSet(dataset);
        configuration.setConsumeMsg(true);


    }

    @EnvironmentalTest
    public void readMessage() {
        final String configStr = SimpleFactory.configurationByExample().forInstance(configuration).configured().toQueryString();

        Job.components()
                .component("source", "PubSub://PubSubInput?" + configStr)
                .component("target", "test://collector")
                .connections()
                .from("source").to("target")
                .build()
                .property("streaming.maxRecords", 5)
                .property("streaming.maxDurationMs", 10_000)
                .run();

       List<Record> records = componentsHandler.getCollectedData(Record.class);
       log.info(records.toString());





    }

}
