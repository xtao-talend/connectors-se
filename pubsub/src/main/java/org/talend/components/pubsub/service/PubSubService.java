package org.talend.components.pubsub.service;

import lombok.extern.slf4j.Slf4j;
import org.talend.components.pubsub.datastore.PubSubDataStore;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.healthcheck.HealthCheck;
import org.talend.sdk.component.api.service.healthcheck.HealthCheckStatus;

@Slf4j
@Service
public class PubSubService {

    public static final String ACTION_HEALTH_CHECK = "HEALTH_CHECK";

    @Service
    private I18nMessage i18n;

    @HealthCheck(ACTION_HEALTH_CHECK)
    public HealthCheckStatus validateDataStore(@Option final PubSubDataStore dataStore) {
//        try {
//            PubSubClient client = PubSubClient.createClient(dataStore);
//            client.listTopics();
//        } catch (final Exception e) {
//            log.error("[HealthCheckStatus] {}", e.getMessage());
//            return new HealthCheckStatus(HealthCheckStatus.Status.KO, e.getMessage());
//        }
        return new HealthCheckStatus(HealthCheckStatus.Status.OK, i18n.successConnection());
    }

}
