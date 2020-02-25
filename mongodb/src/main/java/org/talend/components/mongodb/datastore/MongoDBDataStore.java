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
package org.talend.components.mongodb.datastore;

import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.action.Checkable;
import org.talend.sdk.component.api.configuration.constraint.Min;
import org.talend.sdk.component.api.configuration.constraint.Required;
import org.talend.sdk.component.api.configuration.type.DataStore;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.configuration.ui.widget.Credential;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;

import lombok.Data;

@Version(1)
@Data
@DataStore("MongoDBDataStore")
@Checkable("healthCheck")
@GridLayout(names = GridLayout.FormType.MAIN, value = { @GridLayout.Row({ "host" }), @GridLayout.Row({ "port" }) })
// @GridLayout(names = GridLayout.FormType.ADVANCED, value = { @GridLayout.Row({ "todo" }) })
@Documentation("MongoDB connection")
public class MongoDBDataStore implements Serializable {

    @Option
    @Required
    @Documentation("Server host address")
    private String host;

    @Option
    @Required
    @Documentation("Server port")
    private String port;

    @Option
    @Required
    @Documentation("Database")
    private String database;

}