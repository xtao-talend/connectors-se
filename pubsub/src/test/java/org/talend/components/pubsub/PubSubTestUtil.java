package org.talend.components.pubsub;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.talend.components.pubsub.datastore.PubSubDataStore;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Slf4j
public final class PubSubTestUtil {

    public static String GOOGLE_APPLICATION_CREDENTIALS;

    public static String GOOGLE_PROJECT;

    static {
        GOOGLE_APPLICATION_CREDENTIALS = Optional.ofNullable(System.getProperty("GOOGLE_APPLICATION_CREDENTIALS"))
                .orElseGet(() -> Optional.ofNullable(System.getenv("GOOGLE_APPLICATION_CREDENTIALS"))
                        .orElseThrow(() -> new RuntimeException("GOOGLE_APPLICATION_CREDENTIALS not set")));

        GOOGLE_PROJECT = Optional.ofNullable(System.getProperty("GOOGLE_PROJECT")).orElse("engineering-152721");
        log.info("Using " + GOOGLE_APPLICATION_CREDENTIALS + " as Google credentials file");
    }

    public static PubSubDataStore getDataStore() {
        String jsonCredentials = "";
        try (FileInputStream in = new FileInputStream(GOOGLE_APPLICATION_CREDENTIALS);
             BufferedInputStream bIn = new BufferedInputStream(in)) {
            byte[] buffer = new byte[1024];
            int read = 0;
            while ((read = bIn.read(buffer)) > 0) {
                jsonCredentials += new String(buffer, 0, read, StandardCharsets.UTF_8);
            }
            jsonCredentials = jsonCredentials.replace("\n", " ").trim();
        } catch (IOException ioe) {
            Assertions.fail(ioe);
        }

        PubSubDataStore dataStore = new PubSubDataStore();
        dataStore.setProjectName(GOOGLE_PROJECT);
        dataStore.setJsonCredentials(jsonCredentials);

        return dataStore;

    }
}
