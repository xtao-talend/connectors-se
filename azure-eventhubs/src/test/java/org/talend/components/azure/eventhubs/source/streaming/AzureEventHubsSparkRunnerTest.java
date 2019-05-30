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
 *
 */

package org.talend.components.azure.eventhubs.source.streaming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.talend.sdk.component.junit.SimpleFactory.configurationByExample;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.beam.runners.spark.SparkContextOptions;
import org.apache.beam.runners.spark.SparkRunner;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.PBegin;
import org.apache.beam.sdk.values.PCollection;
import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaSparkContext;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.talend.components.azure.common.Protocol;
import org.talend.components.azure.common.connection.AzureStorageConnectionAccount;
import org.talend.components.azure.eventhubs.AzureEventHubsTestBase;
import org.talend.components.azure.eventhubs.dataset.AzureEventHubsDataSet;
import org.talend.components.azure.eventhubs.output.AzureEventHubsOutputConfiguration;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit.SimpleFactory;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.maven.MavenDecrypter;
import org.talend.sdk.component.maven.Server;
import org.talend.sdk.component.runtime.beam.TalendIO;
import org.talend.sdk.component.runtime.manager.ComponentManager;
import org.talend.sdk.component.runtime.manager.chain.Job;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Disabled("Run manually follow the comment")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WithComponents("org.talend.components.azure.eventhubs")
class AzureEventHubsSparkRunnerTest extends AzureEventHubsTestBase {

    protected static final String EVENTHUB_NAME = "eh-streaming-test";

    private static final String UNIQUE_ID;

    private static final String ACCOUNT_NAME;

    private static final String ACCOUNT_KEY;

    static {
        final MavenDecrypter decrypter = new MavenDecrypter();
        final Server storageAccount = decrypter.find("azure-storage-account");
        ACCOUNT_NAME = storageAccount.getUsername();
        ACCOUNT_KEY = storageAccount.getPassword();

        UNIQUE_ID = Integer.toString(ThreadLocalRandom.current().nextInt(1, 100000));
    }

    // @BeforeAll
    void prepareData() {
        log.warn("a) Eventhub \"" + EVENTHUB_NAME + "\" was created ? ");
        log.warn("b) Partition count is 4 ? ");
        log.warn("c) Consume group \"" + CONSUME_GROUP + "\" ?");
        for (int index = 0; index < 4; index++) {
            AzureEventHubsOutputConfiguration outputConfiguration = new AzureEventHubsOutputConfiguration();
            final AzureEventHubsDataSet dataSet = new AzureEventHubsDataSet();
            dataSet.setEventHubName(EVENTHUB_NAME);
            outputConfiguration.setPartitionType(AzureEventHubsOutputConfiguration.PartitionType.SPECIFY_PARTITION_ID);
            outputConfiguration.setPartitionId(Integer.toString(index));
            dataSet.setConnection(getDataStore());

            outputConfiguration.setDataset(dataSet);

            RecordBuilderFactory factory = getComponentsHandler().findService(RecordBuilderFactory.class);
            List<Record> records = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                records.add(factory.newRecordBuilder().withString("pk", "talend_pk_1")
                        .withString("Name", "TestName_" + i + "_" + UNIQUE_ID).build());
            }

            final String config = configurationByExample().forInstance(outputConfiguration).configured().toQueryString();
            getComponentsHandler().setInputData(records);
            Job.components().component("emitter", "test://emitter")
                    .component("azureeventhubs-output", "AzureEventHubs://AzureEventHubsOutput?" + config).connections()
                    .from("emitter").to("azureeventhubs-output").build().run();
            getComponentsHandler().resetState();
        }
    }

    @Test
    void testStreamingInput() {
        final String containerName = "eventhub-test-streaming";
        AzureEventHubsStreamInputConfiguration inputConfiguration = new AzureEventHubsStreamInputConfiguration();
        final AzureEventHubsDataSet dataSet = new AzureEventHubsDataSet();
        dataSet.setConnection(getDataStore());
        dataSet.setEventHubName(EVENTHUB_NAME);

        AzureStorageConnectionAccount connectionAccount = new AzureStorageConnectionAccount();
        connectionAccount.setAccountName(ACCOUNT_NAME);
        connectionAccount.setProtocol(Protocol.HTTPS);
        connectionAccount.setAccountKey(ACCOUNT_KEY);

        inputConfiguration.setConsumerGroupName(CONSUME_GROUP);
        inputConfiguration.setDataset(dataSet);
        inputConfiguration.setStorageConn(connectionAccount);
        inputConfiguration.setContainerName(containerName);

        final Map<String, String> map = SimpleFactory.configurationByExample().forInstance(inputConfiguration).configured()
                .toMap();

        final ComponentManager manager = ComponentManager.instance();

        PTransform<PBegin, PCollection<Record>> instance = TalendIO
                .read(manager.findMapper("AzureEventHubs", "AzureEventHubsInputStream", 1, map).get());

        Pipeline pipeline = createPipeline();
        PCollection<Record> input = pipeline.apply(instance);
        PAssert.that(input).satisfies(input1 -> {
            Record record = input1.iterator().next();
            assertNotNull(record);
            try {
                log.info(record.toString());
            } catch (Exception e) {
            }
            return null;
        });
        pipeline.run().waitUntilFinish();
    }

    public Pipeline createPipeline() {
        PipelineOptions options = PipelineOptionsFactory.create();
        SparkContextOptions sparkOpts = options.as(SparkContextOptions.class);

        SparkConf conf = new SparkConf();
        conf.setAppName("test");
        conf.setMaster("local[2]");
        conf.set("spark.driver.allowMultipleContexts", "true");
        JavaSparkContext jsc = new JavaSparkContext(new SparkContext(conf));
        sparkOpts.setProvidedSparkContext(jsc);
        sparkOpts.setUsesProvidedSparkContext(true);
        sparkOpts.setRunner(SparkRunner.class);

        return Pipeline.create(sparkOpts);
    }

}