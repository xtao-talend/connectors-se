package org.talend.components.common.stream.api;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.talend.components.common.stream.api.input.RecordReader;
import org.talend.components.common.stream.api.input.RecordReaderSupplier;
import org.talend.components.common.stream.format.json.JsonConfiguration;
import org.talend.components.common.stream.input.json.JsonReaderSupplier;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.input.Emitter;
import org.talend.sdk.component.api.input.Producer;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;

import javax.json.JsonReaderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Iterator;

@Slf4j
@Version(1)
@Icon(Icon.IconType.STAR)
@Emitter(name = "jsonInput")
@Documentation("")
public class JsonEmitter implements Serializable {

    private JsonReaderFactory jsonReaderFactory;

    private RecordBuilderFactory recordBuilderFactory;

    private final Config config;

    private transient boolean done = false;

    private transient Iterator<Record> records = null;

    public JsonEmitter(@Option("configuration") final Config config, JsonReaderFactory jsonReaderFactory, RecordBuilderFactory recordBuilderFactory) {
        this.config = config;
        this.jsonReaderFactory = jsonReaderFactory;
        this.recordBuilderFactory = recordBuilderFactory;
    }

    @Producer
    public Record next() {
        if (done) {
            return null;
        }

        if(records == null) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(config.getJsonFile())) {
                final RecordReaderSupplier recordReaderSupplier = new JsonReaderSupplier();
                final RecordReader reader = recordReaderSupplier.getReader(recordBuilderFactory, config);
                records = reader.read(in);
            } catch (IOException e) {
                log.error("Can't read json file.", e);
            }
        }

        if (!records.hasNext()) {
            records = null;
            done = true;
            return null;
        }

        final Record next = records.next();
        return next;
    }

    @Data
    public static class Config extends JsonConfiguration {
        @Option
        String jsonFile;
    }

}
