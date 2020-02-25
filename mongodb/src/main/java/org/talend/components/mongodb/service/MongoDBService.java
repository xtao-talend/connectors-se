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
package org.talend.components.mongodb.service;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talend.components.mongodb.PathMapping;
import org.talend.components.mongodb.dataset.MongoDBDataSet;
import org.talend.components.mongodb.datastore.MongoDBDataStore;
import org.talend.components.mongodb.source.MongoDBReader;
import org.talend.components.mongodb.source.MongoDBSourceConfiguration;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.healthcheck.HealthCheck;
import org.talend.sdk.component.api.service.healthcheck.HealthCheckStatus;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.api.service.schema.DiscoverSchema;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.talend.sdk.component.api.record.Schema.Type.*;

@Version(1)
@Slf4j
@Service
public class MongoDBService {

    private static final transient Logger LOG = LoggerFactory.getLogger(MongoDBService.class);

    @Service
    private I18nMessage i18n;

    @Service
    private RecordBuilderFactory builderFactory;

    public MongoClient createClient(MongoDBDataStore datastore) {
        String host = datastore.getHost();
        String port = datastore.getPort();

        String uri = "mongodb://" + host + ":" + port;
        MongoClientOptions.Builder optionsBuilder = new MongoClientOptions.Builder();

        try {
            MongoClient mongoClient = new MongoClient(new MongoClientURI(uri, optionsBuilder));
            return mongoClient;
        } catch (Exception e) {
            LOG.error(i18n.example("p1", "p2"));
            throw new RuntimeException(e);
        }
    }

    @HealthCheck("healthCheck")
    public HealthCheckStatus healthCheck(@Option("configuration.dataset.connection") final MongoDBDataStore datastore) {
        try (MongoClient client = createClient(datastore)) {
            String database = datastore.getDatabase();
            if (client.getDatabase(database) == null) {
                return new HealthCheckStatus(HealthCheckStatus.Status.OK, "Can't find the database : " + database);
            }
            return new HealthCheckStatus(HealthCheckStatus.Status.OK, "Connection OK");
        } catch (Exception exception) {
            String message = exception.getMessage();
            LOG.error(message, exception);
            return new HealthCheckStatus(HealthCheckStatus.Status.KO, message);
        }
    }

    public Schema retrieveSchema(@Option("dataset") final MongoDBDataSet dataset) {
        MongoDBSourceConfiguration configuration = new MongoDBSourceConfiguration();
        configuration.setDataset(dataset);
        MongoDBReader reader = new MongoDBReader(configuration, this, builderFactory, i18n);
        reader.init();
        Record record = reader.next();
        reader.release();

        return record.getSchema();
    }

    public void closeClient(MongoClient client) {
        try {
            client.close();
        } catch (Exception e) {
            LOG.warn("Error closing MongoDB client", e);
        }
    }

    public Schema createSchema(Document document, List<PathMapping> pathMappings) {
        Schema.Builder schemaBuilder = builderFactory.newSchemaBuilder(RECORD);

        if (pathMappings == null || pathMappings.isEmpty()) {// work for the next level element when RECORD, not necessary now,
                                                             // but keep it
            pathMappings = new ArrayList<>();
            // order keep here as use LinkedHashMap/LinkedHashSet inside
            Set<String> elements = document.keySet();
            for (String element : elements) {
                // TODO make the column name in schema is valid without special char that make invalid to schema
                // para1 : column name in schema, para2 : key in document of mongodb, para3 : path to locate in document of
                // mongodb
                pathMappings.add(new PathMapping(element, element, element));
            }
        }

        for (PathMapping mapping : pathMappings) {
            // column for flow struct
            String column = mapping.getColumn();
            // the mongodb's origin element name in bson
            String originElement = mapping.getOriginElement();
            // path to locate the element of value provider of bson object
            String path = mapping.getPath();

            // receive value from JSON
            Object value = getValueByPathFromDocument(document, path, originElement);

            // With this value we can define type
            Schema.Type type = guessFieldTypeFromValueFromBSON(value);

            // We can add to schema builder entry
            Schema.Entry.Builder entryBuilder = builderFactory.newEntryBuilder();
            entryBuilder.withNullable(true).withName(column).withType(type);

            // copy from couchbase, not work in fact, but keep it for future, maybe necessary
            if (type == RECORD) {
                entryBuilder.withElementSchema(createSchema((Document) value, null));
            } else if (type == ARRAY) {
                // not sure api is using List object for array, TODO check it
                entryBuilder.withElementSchema(defineSchemaForArray((List) value));
            }
            Schema.Entry currentEntry = entryBuilder.build();
            schemaBuilder.withEntry(currentEntry);
        }
        return schemaBuilder.build();
    }

    // use column diretly if path don't exists or empty
    // current implement logic copy from studio one, not sure is expected, TODO adjust it
    public Object getValueByPathFromDocument(Document document, String path, String elementName) {
        if (document == null) {
            return null;
        }

        Object value = null;
        if (path == null || "".equals(path)) {// if path is not set, use element name directly
            if ("*".equals(elementName)) {// * mean the whole object?
                value = document;
            } else if (document.get(elementName) != null) {
                value = document.get(elementName);
            }
        } else {
            // use path to locate
            String objNames[] = path.split("\\.");
            Document currentObj = document;
            for (int i = 0; i < objNames.length; i++) {
                currentObj = (Document) currentObj.get(objNames[i]);
                if (currentObj == null) {
                    break;
                }
            }
            if ("*".equals(elementName)) {
                value = currentObj;
            } else if (currentObj != null) {
                value = currentObj.get(elementName);
            }
        }
        return value;
    }

    private Schema defineSchemaForArray(List jsonArray) {
        Object firstValueInArray = jsonArray.get(0);
        Schema.Builder schemaBuilder = builderFactory.newSchemaBuilder(RECORD);
        if (firstValueInArray == null) {
            throw new IllegalArgumentException("First value of Array is null. Can't define type of values in array");
        }
        Schema.Type type = guessFieldTypeFromValueFromBSON(firstValueInArray);
        schemaBuilder.withType(type);
        if (type == RECORD) {
            schemaBuilder.withEntry(
                    builderFactory.newEntryBuilder().withElementSchema(createSchema((Document) firstValueInArray, null)).build());
        } else if (type == ARRAY) {
            schemaBuilder.withEntry(
                    builderFactory.newEntryBuilder().withElementSchema(defineSchemaForArray((List) firstValueInArray)).build());
        }
        return schemaBuilder.withType(type).build();
    }

    private Schema.Type guessFieldTypeFromValueFromBSON(Object value) {
        if (value instanceof String) {
            return STRING;
        } else if (value instanceof Boolean) {
            return BOOLEAN;
        } else if (value instanceof Date) {
            return DATETIME;
        } else if (value instanceof Double) {
            return DOUBLE;
        } else if (value instanceof Integer) {
            return INT;
        } else if (value instanceof Long) {
            return LONG;
        } else if (value instanceof byte[]) {
            return BYTES;
        } else if (value instanceof List) {// for bson array, not sure api is using List object for array, TODO check it
            // TODO use ARRAY? now only make thing simple
            return STRING;
        } else if (value instanceof Document) {
            // TODO use ARRAY? now only make thing simple
            return STRING;
        } else if (value instanceof Float) {
            return FLOAT;
        } else {
            // null, decimal, also if the value is not basic java type, for example, mongodb defined type, not sure TODO
            return STRING;
        }
    }

}