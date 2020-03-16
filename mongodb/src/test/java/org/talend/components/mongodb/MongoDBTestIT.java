/*
 * Copyright (C) 2006-2020 Talend Inc. - www.talend.com
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
package org.talend.components.mongodb;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.talend.components.mongodb.dataset.MongoDBReadDataSet;
import org.talend.components.mongodb.datastore.MongoDBDataStore;
import org.talend.components.mongodb.service.MongoDBService;
import org.talend.components.mongodb.source.MongoDBQuerySourceConfiguration;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit.BaseComponentsHandler;
import org.talend.sdk.component.junit5.Injected;
import org.talend.sdk.component.junit5.WithComponents;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.talend.sdk.component.junit.SimpleFactory;
import org.talend.sdk.component.runtime.manager.chain.Job;

// TODO current only for local test which have mongo env, will refactor it later
@Slf4j
@WithComponents("org.talend.components.mongodb")
@DisplayName("testing of MongoDB connector")
public class MongoDBTestIT {

    @Injected
    private BaseComponentsHandler componentsHandler;

    @Service
    private RecordBuilderFactory recordBuilderFactory;

    @Service
    private MongoDBService mongoDBService;

    @Test
    void testBasic() {
        MongoDBReadDataSet dataset = getMongoDBDataSet("test");

        List<PathMapping> pathMappings = new ArrayList<>();
        pathMappings.add(new PathMapping("_id", "_id", ""));
        pathMappings.add(new PathMapping("item", "item", ""));
        pathMappings.add(new PathMapping("qty", "qty", ""));
        pathMappings.add(new PathMapping("status", "status", ""));

        //dataset.setMode(Mode.MAPPING);
        dataset.setPathMappings(pathMappings);

        MongoDBQuerySourceConfiguration config = new MongoDBQuerySourceConfiguration();
        config.setDataset(dataset);

        executeJob(config);
        final List<Record> res = componentsHandler.getCollectedData(Record.class);

        System.out.println(res);
    }

    @Test
    void testDate() {
        MongoDBReadDataSet dataset = getMongoDBDataSet("bakesales");

        List<PathMapping> pathMappings = new ArrayList<>();
        pathMappings.add(new PathMapping("_id", "_id", ""));
        pathMappings.add(new PathMapping("date", "date", ""));
        pathMappings.add(new PathMapping("quantity", "quantity", ""));
        pathMappings.add(new PathMapping("amount", "amount", ""));

        //dataset.setMode(Mode.MAPPING);
        dataset.setPathMappings(pathMappings);

        MongoDBQuerySourceConfiguration config = new MongoDBQuerySourceConfiguration();
        config.setDataset(dataset);

        executeJob(config);
        final List<Record> res = componentsHandler.getCollectedData(Record.class);

        System.out.println(res);
    }

    @Test
    void testFind() {
        MongoDBReadDataSet dataset = getMongoDBDataSet("test");

        dataset.setQuery("{status : \"A\"}");
        dataset.setMode(Mode.TEXT);

        MongoDBQuerySourceConfiguration config = new MongoDBQuerySourceConfiguration();
        config.setDataset(dataset);

        executeJob(config);
        final List<Record> res = componentsHandler.getCollectedData(Record.class);

        System.out.println(res);
    }

    /*
     * @Test
     * void testAggregation() {
     * MongoDBReadDataSet dataset = getMongoDBDataSet("bakesales");
     * 
     * List<PathMapping> pathMappings = new ArrayList<>();
     * pathMappings.add(new PathMapping("_id", "_id", ""));
     * pathMappings.add(new PathMapping("sales_quantity", "sales_quantity", ""));
     * pathMappings.add(new PathMapping("sales_amount", "sales_amount", ""));
     * 
     * dataset.setPathMappings(pathMappings);
     * 
     * List<AggregationStage> stages = new ArrayList<>();
     * AggregationStage stage = new AggregationStage();
     * stage.setStage("{ $match: { date: { $gte: new ISODate(\"2018-12-05\") } } }");
     * stages.add(stage);
     * 
     * stage = new AggregationStage();
     * stage.setStage(
     * "{ $group: { _id: { $dateToString: { format: \"%Y-%m\", date: \"$date\" } }, sales_quantity: { $sum: \"$quantity\"}, sales_amount: { $sum: \"$amount\" } } }"
     * );
     * stages.add(stage);
     * 
     * dataset.setQueryType(QueryType.AGGREGATION);
     * dataset.setAggregationStages(stages);
     * 
     * MongoDBQuerySourceConfiguration config = new MongoDBQuerySourceConfiguration();
     * config.setDataset(dataset);
     * 
     * executeJob(config);
     * final List<Record> res = componentsHandler.getCollectedData(Record.class);
     * 
     * System.out.println(res);
     * }
     */

    private MongoDBReadDataSet getMongoDBDataSet(String collection) {
        MongoDBDataStore datastore = new MongoDBDataStore();
        datastore.setAddress(new Adress("localhost", "27017"));
        datastore.setDatabase("test");

        MongoDBReadDataSet dataset = new MongoDBReadDataSet();
        dataset.setDatastore(datastore);
        dataset.setCollection(collection);
        return dataset;
    }

    @Test
    void testBasicDocumentMode() {
        MongoDBReadDataSet dataset = getMongoDBDataSet("test");
        dataset.setMode(Mode.TEXT);

        MongoDBQuerySourceConfiguration config = new MongoDBQuerySourceConfiguration();
        config.setDataset(dataset);

        executeJob(config);
        final List<Record> res = componentsHandler.getCollectedData(Record.class);

        System.out.println(res);
    }

    @Test
    void testHealthCheck() {
        MongoDBDataStore datastore = new MongoDBDataStore();
        datastore.setAddress(new Adress("localhost", "27017"));
        datastore.setDatabase("test1");

        System.out.println(mongoDBService.healthCheck(datastore));
    }

    private void executeJob(MongoDBQuerySourceConfiguration configuration) {
        final String inputConfig = SimpleFactory.configurationByExample().forInstance(configuration).configured().toQueryString();
        Job.components().component("MongoDB_CollectionQuerySource", "MongoDB://CollectionQuerySource?" + inputConfig)
                .component("collector", "test://collector").connections().from("MongoDB_CollectionQuerySource").to("collector")
                .build().run();
    }

}
