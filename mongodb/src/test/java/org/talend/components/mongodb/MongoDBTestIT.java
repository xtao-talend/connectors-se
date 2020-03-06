package org.talend.components.mongodb;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.talend.components.mongodb.dataset.MongoDBDataSet;
import org.talend.components.mongodb.datastore.MongoDBDataStore;
import org.talend.components.mongodb.service.I18nMessage;
import org.talend.components.mongodb.service.MongoDBService;
import org.talend.components.mongodb.source.MongoDBReader;
import org.talend.components.mongodb.source.MongoDBSourceConfiguration;
import org.talend.sdk.component.api.configuration.Option;
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

//TODO current only for local test which have mongo env, will refactor it later
@Slf4j
@WithComponents("org.talend.components.mongodb")
@DisplayName("testing of MongoDB connector")
public class MongoDBTestIT {

    @Injected
    protected BaseComponentsHandler componentsHandler;

    @Service
    protected RecordBuilderFactory recordBuilderFactory;

    @Test
    void testBasic() {
        MongoDBDataSet dataset = getMongoDBDataSet("test");

        List<PathMapping> pathMappings = new ArrayList<>();
        pathMappings.add(new PathMapping("_id", "_id", ""));
        pathMappings.add(new PathMapping("item", "item", ""));
        pathMappings.add(new PathMapping("qty", "qty", ""));
        pathMappings.add(new PathMapping("status", "status", ""));

        dataset.setPathMappings(pathMappings);

        MongoDBSourceConfiguration config = new MongoDBSourceConfiguration();
        config.setDataset(dataset);

        executeJob(config);
        final List<Record> res = componentsHandler.getCollectedData(Record.class);

        System.out.println(res);
    }

    @Test
    void testDate() {
        MongoDBDataSet dataset = getMongoDBDataSet("bakesales");

        List<PathMapping> pathMappings = new ArrayList<>();
        pathMappings.add(new PathMapping("_id", "_id", ""));
        pathMappings.add(new PathMapping("date", "date", ""));
        pathMappings.add(new PathMapping("quantity", "quantity", ""));
        pathMappings.add(new PathMapping("amount", "amount", ""));

        dataset.setPathMappings(pathMappings);

        MongoDBSourceConfiguration config = new MongoDBSourceConfiguration();
        config.setDataset(dataset);

        executeJob(config);
        final List<Record> res = componentsHandler.getCollectedData(Record.class);

        System.out.println(res);
    }

    @Test
    void testFind() {
        MongoDBDataSet dataset = getMongoDBDataSet("test");

        dataset.setQuery("{status : \"A\"}");
        dataset.setMode(Mode.DOCUMENT);

        MongoDBSourceConfiguration config = new MongoDBSourceConfiguration();
        config.setDataset(dataset);

        executeJob(config);
        final List<Record> res = componentsHandler.getCollectedData(Record.class);

        System.out.println(res);
    }

    @Test
    void testAggregation() {
        MongoDBDataSet dataset = getMongoDBDataSet("bakesales");

        List<PathMapping> pathMappings = new ArrayList<>();
        pathMappings.add(new PathMapping("_id", "_id", ""));
        pathMappings.add(new PathMapping("sales_quantity", "sales_quantity", ""));
        pathMappings.add(new PathMapping("sales_amount", "sales_amount", ""));

        dataset.setPathMappings(pathMappings);

        dataset.setQueryType(QueryType.AGGREGATION);
        dataset.setAggregationStages(Arrays.asList("{ $match: { date: { $gte: new ISODate(\"2018-12-05\") } } }", "{ $group: { _id: { $dateToString: { format: \"%Y-%m\", date: \"$date\" } }, sales_quantity: { $sum: \"$quantity\"}, sales_amount: { $sum: \"$amount\" } } }"));

        MongoDBSourceConfiguration config = new MongoDBSourceConfiguration();
        config.setDataset(dataset);

        executeJob(config);
        final List<Record> res = componentsHandler.getCollectedData(Record.class);

        System.out.println(res);
    }

    private MongoDBDataSet getMongoDBDataSet(String collection) {
        MongoDBDataStore datastore = new MongoDBDataStore();
        datastore.setAddress(new Adress("localhost", "27017"));
        datastore.setDatabase("test");

        MongoDBDataSet dataset = new MongoDBDataSet();
        dataset.setDatastore(datastore);
        dataset.setCollection(collection);
        return dataset;
    }

    @Test
    void testBasicDocumentMode() {
        MongoDBDataSet dataset = getMongoDBDataSet("test");
        dataset.setMode(Mode.DOCUMENT);

        MongoDBSourceConfiguration config = new MongoDBSourceConfiguration();
        config.setDataset(dataset);

        executeJob(config);
        final List<Record> res = componentsHandler.getCollectedData(Record.class);

        System.out.println(res);
    }

    private void executeJob(MongoDBSourceConfiguration configuration) {
        final String inputConfig = SimpleFactory.configurationByExample().forInstance(configuration).configured().toQueryString();
        Job.components().component("MongoDB_Source", "MongoDB://Source?" + inputConfig)
                .component("collector", "test://collector").connections().from("MongoDB_Source").to("collector").build().run();
    }

}
