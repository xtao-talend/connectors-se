package org.talend.components.pubsub.input;

import org.talend.sdk.component.runtime.manager.chain.Job;

public class PubSubInputJobThread extends Thread {

    private String configStr;

    public PubSubInputJobThread(String name, String configStr) {
        super(name);
        this.configStr = configStr;
    }

    @Override
    public void run() {
        Job.components()
                .component("source", "PubSub://PubSubInput?" + configStr)
                .component("target", "test://collector")
                .connections()
                .from("source").to("target")
                .build()
                .run();
    }

}
