/*
 * Copyright (C) 2006-2019 Talend Inc. - www.talend.com
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
package org.talend.components.jdbc.dataset;

import lombok.Data;
import lombok.experimental.Delegate;
import org.talend.components.jdbc.datastore.JdbcConnection;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.action.Suggestable;
import org.talend.sdk.component.api.configuration.action.Validable;
import org.talend.sdk.component.api.configuration.constraint.Required;
import org.talend.sdk.component.api.configuration.type.DataSet;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.configuration.ui.widget.Code;
import org.talend.sdk.component.api.meta.Documentation;

import static org.talend.components.jdbc.output.platforms.PlatformFactory.get;
import static org.talend.components.jdbc.service.UIActionService.ACTION_SUGGESTION_TABLE_NAMES;
import static org.talend.components.jdbc.service.UIActionService.ACTION_VALIDATION_READONLY_QUERY;
import static org.talend.sdk.component.api.configuration.ui.layout.GridLayout.FormType.ADVANCED;

@Data
@DataSet("ChangeDataCaptureDataset")
@GridLayout({ @GridLayout.Row("connection"), @GridLayout.Row("tableName"), @GridLayout.Row("streamTableName") })
@GridLayout(names = ADVANCED, value = { @GridLayout.Row("connection"), @GridLayout.Row("advancedCommon") })
@Documentation("This configuration define a dataset using a from a Snowflake stream table.\n")
public class ChangeDataCaptureDataset implements BaseDataSet {

    /**
     * For now only Snowflake
     */

    @Option
    @Documentation("the connection information to execute the query")
    private JdbcConnection connection;

    @Option
    @Required
    @Documentation("The table name")
    @Suggestable(value = ACTION_SUGGESTION_TABLE_NAMES, parameters = "connection")
    private String tableName;

    @Option
    @Required
    @Documentation("The stream table name")
    private String streamTableName;

    @Option
    @Delegate
    @Documentation("common input configuration")
    private AdvancedCommon advancedCommon = new AdvancedCommon();

    @Override
    public String getQuery() {
        // No need for the i18n service for this instance
        return "select * from " + get(connection, null).identifier(getStreamTableName());
    }

    private String getQN(String db, String schema, String table) {
        return db + "." + schema + "." + table;
    }

    // Snowflake CDC specific !!!
    public String createStreamTableIfNotExist() {

        // hardcoded for tests, need to be retrieved from JDBC url
        String dbName = "DEMO_DB";
        String schema = "PUBLIC";
        //

        return "create stream if not exists " + getQN(dbName, schema, streamTableName) + " on table "
                + getQN(dbName, schema, tableName);
    }
}
