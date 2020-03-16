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
package org.talend.components.mongodb.sink;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.talend.components.mongodb.Mode;
import org.talend.components.mongodb.PathMapping;
import org.talend.components.mongodb.dataset.MongoDBReadAndWriteDataSet;
import org.talend.components.mongodb.dataset.MongoDBReadDataSet;
import org.talend.components.mongodb.datastore.MongoDBDataStore;
import org.talend.components.mongodb.service.I18nMessage;
import org.talend.components.mongodb.service.MongoDBService;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.processor.ElementListener;
import org.talend.sdk.component.api.processor.Input;
import org.talend.sdk.component.api.processor.Processor;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.record.Schema;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Version(1)
@Slf4j
@Icon(value = Icon.IconType.CUSTOM, custom = "MongoDBSink")
@Processor(name = "Sink")
@Documentation("This component writes data to MongoDB")
public class MongoDBProcessor implements Serializable {

    private I18nMessage i18n;

    private final MongoDBSinkConfiguration configuration;

    private final MongoDBService service;

    private transient MongoClient client;

    private transient MongoCollection<Document> collection;

    public MongoDBProcessor(@Option("configuration") final MongoDBSinkConfiguration configuration, final MongoDBService service,
            final I18nMessage i18n) {
        this.configuration = configuration;
        this.service = service;
        this.i18n = i18n;
    }

    @PostConstruct
    public void init() {
        MongoDBReadAndWriteDataSet dataset = configuration.getDataset();
        MongoDBDataStore datastore = dataset.getDatastore();
        client = service.createClient(datastore);
        MongoDatabase database = client.getDatabase(datastore.getDatabase());
        MongoCollection<Document> collection = database.getCollection(dataset.getCollection());
    }

    private class DocumentGenerator {

        private Document document;

        private DocumentGenerator() {
            document = new Document();
        }

        void put(String parentNodePath, String curentName, Object value) {
            if (parentNodePath == null || "".equals(parentNodePath)) {
                document.put(curentName, value);
            } else {
                String objNames[] = parentNodePath.split("\\.");
                Document lastNode = getParentNode(parentNodePath, objNames.length - 1);
                lastNode.put(curentName, value);
                Document parentNode = null;
                for (int i = objNames.length - 1; i >= 0; i--) {
                    parentNode = getParentNode(parentNodePath, i - 1);
                    parentNode.put(objNames[i], lastNode);
                    lastNode = clone(parentNode);
                }
                document = lastNode;
            }
        }

        private Document clone(Document source) {
            Document to = new Document();
            for (java.util.Map.Entry<String, Object> cur : source.entrySet()) {
                to.append(cur.getKey(), cur.getValue());
            }
            return to;
        }

        public Document getParentNode(String parentNodePath, int index) {
            Document parentNode = document;
            if (parentNodePath == null || "".equals(parentNodePath)) {
                return document;
            } else {
                String objNames[] = parentNodePath.split("\\.");
                for (int i = 0; i <= index; i++) {
                    parentNode = (Document) parentNode.get(objNames[i]);
                    if (parentNode == null) {
                        parentNode = new Document();
                        return parentNode;
                    }
                    if (i == index) {
                        break;
                    }
                }
                return parentNode;
            }
        }

        Document getDocument() {
            return this.document;
        }
    }

    @ElementListener
    public void onNext(@Input final Record record) {
        if (configuration.getDataset().getMode() == Mode.TEXT) {
            // we store the whole document here as a string
            String uniqueFieldName = record.getSchema().getEntries().get(0).getName();
            String value = record.getString(uniqueFieldName);
            Document document = Document.parse(value);
            collection.insertOne(document);
        } else {
            DocumentGenerator dg = new DocumentGenerator();
            List<PathMapping> mappings = configuration.getDataset().getPathMappings();
            Map<String, PathMapping> inputFieldName2PathMapping = new LinkedHashMap<>();

            // TODO now only use name mapping, improve it with index mapping
            for (PathMapping mapping : mappings) {
                String column = mapping.getColumn();
                inputFieldName2PathMapping.put(column, mapping);
            }

            for (Schema.Entry entry : record.getSchema().getEntries()) {// schema from input
                PathMapping mapping = inputFieldName2PathMapping.get(entry.getName());
                String originElement = mapping.getOriginElement();
                dg.put(mapping.getParentNodePath(), originElement != null ? originElement : entry.getName(),
                        record.get(Object.class, entry.getName()));
            }

            collection.insertOne(dg.getDocument());
        }
    }

    @PreDestroy
    public void release() {
        service.closeClient(client);
    }

    // copy from couchbase, not use now, will use it maybe
    private Object jsonValueFromRecordValue(Schema.Entry entry, Record record) {
        String entryName = entry.getName();
        Object value = record.get(Object.class, entryName);
        if (null == value) {
            // TODO check use what explain null
            return "";
        }
        switch (entry.getType()) {
        case INT:
            return record.getInt(entryName);
        case LONG:
            return record.getLong(entryName);
        case BYTES:
            return java.util.Base64.getEncoder().encodeToString(record.getBytes(entryName));
        case FLOAT:
            return Double.parseDouble(String.valueOf(record.getFloat(entryName)));
        case DOUBLE:
            return record.getDouble(entryName);
        case STRING:
            return createJsonFromString(record.getString(entryName));
        case BOOLEAN:
            return record.getBoolean(entryName);
        case ARRAY:
            return record.getArray(List.class, entryName);
        case DATETIME:
            return record.getDateTime(entryName).toString();
        case RECORD:
            return record.getRecord(entryName);
        default:
            throw new IllegalArgumentException("Unknown Type " + entry.getType());
        }
    }

    private Object createJsonFromString(String str) {
        Object value = null;
        try {
            value = Document.parse(str);
        } catch (Exception e) {
            // can't create JSON object from String ignore exception
            // and try to create JSON array
        } finally {
            if (value != null)
                return value;
        }
        // TODO consider array case
        /*
         * try {
         * value = Document.fromArray(str);
         * } catch (Exception e) {
         * value = str;
         * }
         */
        return value;
    }

}
