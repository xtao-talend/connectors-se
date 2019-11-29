package org.talend.components.pubsub.datastore;

import lombok.Data;
import org.talend.components.pubsub.service.PubSubService;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.action.Checkable;
import org.talend.sdk.component.api.configuration.constraint.Required;
import org.talend.sdk.component.api.configuration.type.DataStore;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.configuration.ui.widget.Credential;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;

@DataStore
@Data
@Checkable(PubSubService.ACTION_HEALTH_CHECK)
@GridLayout({ //
        @GridLayout.Row({ "projectName" }), //
        @GridLayout.Row("jsonCredentials") //
})
@Documentation("Pub/Sub DataStore Properties")
public class PubSubDataStore implements Serializable {

    @Option
    @Required
    @Documentation("Google Cloud Platform Project")
    private String projectName;

    @Option
    @Credential
    @Required
    @Documentation("Google credential (JSON)")
    private String jsonCredentials;

}
