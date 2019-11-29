package org.talend.components.pubsub.dataset;

import lombok.Data;
import org.talend.components.pubsub.datastore.PubSubDataStore;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.type.DataSet;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;

@Data
@DataSet("PubSubDataSet")
@Documentation("Pub/Sub Dataset Properties")
public class PubSubDataSet implements Serializable {

    @Option
    @Documentation("Connection")
    private PubSubDataStore dataStore;
}
