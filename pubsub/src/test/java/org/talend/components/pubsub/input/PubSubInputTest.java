package org.talend.components.pubsub.input;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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
        componentsHandler.injectServices(this);

        PubSubDataStore dataStore = PubSubTestUtil.getDataStore();

        PubSubDataSet dataset = new PubSubDataSet();
        dataset.setDataStore(dataStore);
        dataset.setTopic("rlecomteTopic");
        dataset.setSubscription("ITSub");
        dataset.setValueFormat(PubSubDataSet.ValueFormat.JSON);

        configuration = new PubSubInputConfiguration();
        configuration.setDataSet(dataset);
        configuration.setConsumeMsg(true);


    }

    @EnvironmentalTest
    public void readMessage() {
        final String configStr = SimpleFactory.configurationByExample().forInstance(configuration).configured().toQueryString();

        PubSubInputJobThread jobThread = new PubSubInputJobThread("job", configStr);
        jobThread.start();

        int nbRecordsReceived = 0;
        do {
            try {
                List<Record> records = componentsHandler.getCollectedData(Record.class);
                nbRecordsReceived = records.size();
            } catch (Exception e) {
                // nothing
            }
        } while(nbRecordsReceived < 5);

        try {
            jobThread.stop();
        } catch (Exception e) {
            // nothing
        }

        log.info(componentsHandler.getCollectedData(Record.class).toString());





    }

}
