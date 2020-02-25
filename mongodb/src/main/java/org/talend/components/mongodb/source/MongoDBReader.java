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
package org.talend.components.mongodb.source;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.talend.components.mongodb.PathMapping;
import org.talend.components.mongodb.dataset.MongoDBDataSet;
import org.talend.components.mongodb.datastore.MongoDBDataStore;
import org.talend.components.mongodb.service.I18nMessage;
import org.talend.components.mongodb.service.MongoDBService;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Producer;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

@Slf4j
@Documentation("This component reads data from MongoDB.")
public class MongoDBReader implements Serializable {

    private I18nMessage i18n;

    private static final transient Logger LOG = LoggerFactory.getLogger(MongoDBReader.class);

    private final MongoDBSourceConfiguration configuration;

    private final RecordBuilderFactory builderFactory;

    private MongoDBService service;

    private transient MongoClient client;

    public MongoDBReader(@Option("configuration") final MongoDBSourceConfiguration configuration, final MongoDBService service,
            final RecordBuilderFactory builderFactory, final I18nMessage i18n) {
        this.configuration = configuration;
        this.service = service;
        this.builderFactory = builderFactory;
        this.i18n = i18n;
    }

    Iterator<Document> iterator = null;

    @PostConstruct
    public void init() {
        MongoDBDataSet dataset = configuration.getDataset();
        MongoDBDataStore datastore = dataset.getDatastore();
        client = service.createClient(datastore);
        MongoDatabase database = client.getDatabase(datastore.getDatabase());
        MongoCollection<Document> collection = database.getCollection(dataset.getCollection());

        Iterable iterable = null;
        switch (dataset.getQueryType()) {
        case FIND:
            Document query = Document.parse(dataset.getQuery());
            // FindIterable<Document>
            iterable = collection.find(query);
        case AGGREGATION:
            List<Document> aggregationStages = new ArrayList<Document>();
            // TODO use another table setting to replace it
            Document stage = Document.parse(dataset.getQuery());
            aggregationStages.add(stage);
            // AggregateIterable<Document>
            iterable = collection.aggregate(aggregationStages).allowDiskUse(false);
            break;
        default:
            break;
        }

        iterator = iterable.iterator();
    }

    @Producer
    public Record next() {
        if (iterator.hasNext()) {
            Document document = iterator.next();
            return convertDocument2Record(document);
        }
        return null;
    }

    private Record convertDocument2Record(Document document) {
        switch (configuration.getDataset().getMode()) {
        case DOCUMENT:
            return toRecordWithWSingleDocumentContentColumn(document);
        case MAPPING:
        default:
            return toFlatRecord(document);
        }
    }

    private List<PathMapping> initPathMappings(Document document) {
        List<PathMapping> pathMappings = configuration.getDataset().getPathMappings();
        if (pathMappings == null || pathMappings.isEmpty()) {
            return guessPathMappingsFromDocument(document);
        }
        return pathMappings;
    }

    public List<PathMapping> guessPathMappingsFromDocument(Document document) {
        // TODO
        return null;
    }

    // only create schema by first document and path mapping
    private transient Schema schema;

    private Record toFlatRecord(Document document) {
        List<PathMapping> pathMappings = initPathMappings(document);
        if (schema == null) {
            schema = service.createSchema(document, pathMappings);
        }
        final Record.Builder recordBuilder = builderFactory.newRecordBuilder(schema);
        Iterator<Schema.Entry> entries = schema.getEntries().iterator();
        for (PathMapping mapping : pathMappings) {
            // column for flow struct
            String column = mapping.getColumn();
            // the mongodb's origin element name in bson
            String originElement = mapping.getOriginElement();
            // path to locate the element of value provider of bson object
            String path = mapping.getPath();
            Object value = service.getValueByPathFromDocument(document, path, originElement);

            Schema.Entry entry = entries.next();

            addColumn(recordBuilder, entry, value);
        }
        return recordBuilder.build();
    }

    private Record toRecordWithWSingleDocumentContentColumn(Document document) {
        Schema.Builder schemaBuilder = builderFactory.newSchemaBuilder(Schema.Type.RECORD);

        String singleColumnName = configuration.getDataset().getCollection();
        Schema.Entry.Builder entryBuilder = builderFactory.newEntryBuilder();
        entryBuilder.withNullable(true).withName(singleColumnName).withType(Schema.Type.STRING);
        Schema.Entry singleEntry = entryBuilder.build();
        schemaBuilder.withEntry(singleEntry);

        Schema schemaWithSingleColumn = schemaBuilder.build();

        final Record.Builder recordBuilder = builderFactory.newRecordBuilder(schemaWithSingleColumn);
        addColumn(recordBuilder, singleEntry, document);
        return recordBuilder.build();
    }

    @PreDestroy
    public void release() {
        service.closeClient(client);
    }

    private void addColumn(Record.Builder recordBuilder, final Schema.Entry entry, Object value) {
        final Schema.Entry.Builder entryBuilder = builderFactory.newEntryBuilder();
        Schema.Type type = entry.getType();
        entryBuilder.withName(entry.getName()).withNullable(true).withType(type);

        if (value == null) {
            // TODO check if it is right, when null, no need to fill something in the record?
            return;
        }

        switch (type) {
        case ARRAY:
            // TODO copy from couchbase connector, no use now, keep it for future, maybe not necessary
            Schema elementSchema = entry.getElementSchema();
            entryBuilder.withElementSchema(elementSchema);
            if (elementSchema.getType() == Schema.Type.RECORD) {
                List<Record> recordList = new ArrayList<>();
                // schema of the first element
                Schema currentSchema = elementSchema.getEntries().get(0).getElementSchema();
                for (int i = 0; i < ((List) value).size(); i++) {
                    Document currentJsonObject = (Document) ((List) value).get(i);
                    recordList.add(createRecord(currentSchema, currentJsonObject));
                }
                recordBuilder.withArray(entryBuilder.build(), recordList);
            } else {
                recordBuilder.withArray(entryBuilder.build(), ((List) value));
            }
            break;
        case FLOAT:
            recordBuilder.withFloat(entryBuilder.build(), (Float) value);
            break;
        case DOUBLE:
            recordBuilder.withDouble(entryBuilder.build(), (Double) value);
            break;
        case BYTES:
            recordBuilder.withBytes(entryBuilder.build(), (byte[]) value);
        case STRING:
            // toString is right for all type, like document? TODO
            recordBuilder.withString(entryBuilder.build(), value.toString());
            break;
        case LONG:
            recordBuilder.withLong(entryBuilder.build(), (Long) value);
            break;
        case INT:
            recordBuilder.withInt(entryBuilder.build(), (Integer) value);
            break;
        case DATETIME:
            recordBuilder.withDateTime(entryBuilder.build(), (Date) value);
            break;
        case BOOLEAN:
            recordBuilder.withBoolean(entryBuilder.build(), (Boolean) value);
            break;
        case RECORD:
            // TODO support it in future, maybe not necessary
            entryBuilder.withElementSchema(entry.getElementSchema());
            recordBuilder.withRecord(entryBuilder.build(), createRecord(entry.getElementSchema(), (Document) value));
            break;
        }
    }

    private Record createRecord(Schema schema, Document document) {
        final Record.Builder recordBuilder = builderFactory.newRecordBuilder(schema);
        schema.getEntries().forEach(entry -> addColumn(recordBuilder, entry, getValue(entry.getName(), document)));
        return recordBuilder.build();
    }

    private Object getValue(String currentName, Document document) {
        if (document == null) {
            return null;
        }
        return document.get(currentName);
    }
}