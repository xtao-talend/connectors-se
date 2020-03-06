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

import org.talend.components.mongodb.ReadPreference;
import org.talend.components.mongodb.SortBy;
import org.talend.components.mongodb.dataset.MongoDBDataSet;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.condition.ActiveIf;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayouts;
import org.talend.sdk.component.api.configuration.ui.widget.Code;
import org.talend.sdk.component.api.configuration.ui.widget.TextArea;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import lombok.Data;

@Version(1)
@Data
@GridLayouts({
        @GridLayout({ @GridLayout.Row({ "dataset" }), @GridLayout.Row({ "dataset" }), @GridLayout.Row({ "setReadPreference" }),
                @GridLayout.Row({ "readPreference" }), @GridLayout.Row({ "sortBy" }) }),
        @GridLayout(names = GridLayout.FormType.ADVANCED, value = { @GridLayout.Row({ "limit" }) }) })
@Documentation("MongoDB Source Configuration")
public class MongoDBSourceConfiguration implements Serializable {

    @Option
    @Documentation("dataset")
    private MongoDBDataSet dataset;

    @Option
    @Documentation("Set read preference")
    private boolean setReadPreference;

    @Option
    @ActiveIf(target = "setReadPreference", value = "true")
    @Documentation("Read preference")
    private ReadPreference readPreference = ReadPreference.PRIMARY;

    @Option
    @Documentation("Sort by")
    private List<SortBy> sortBy = Collections.emptyList();

    @Option
    @Documentation("Maximum number of documents to be returned")
    private int limit = -1;

}