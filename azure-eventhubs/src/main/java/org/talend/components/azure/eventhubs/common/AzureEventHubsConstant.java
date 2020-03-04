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
package org.talend.components.azure.eventhubs.common;

import java.nio.charset.Charset;

public class AzureEventHubsConstant {

    public static final String DEFAULT_CONSUMER_GROUP = "$Default";

    /**
     * Represents the setting name for the account key.
     */
    public static final String ACCOUNT_KEY_NAME = "AccountKey";

    /**
     * Represents the setting name for the account name.
     */
    public static final String ACCOUNT_NAME_NAME = "AccountName";

    /**
     * Represents the root storage DNS name.
     */
    public static final String DEFAULT_DNS = "core.windows.net";

    /**
     * Represents the setting name for a custom storage endpoint suffix.
     */
    public static final String ENDPOINT_SUFFIX_NAME = "EndpointSuffix";

    /**
     * The setting name for using the default storage endpoints with the specified protocol.
     */
    public static final String DEFAULT_ENDPOINTS_PROTOCOL_NAME = "DefaultEndpointsProtocol";

    public static final String CHECKPOINTING_EVERY = "CHECKPOINTING_EVERY";

    public static final String PARTITION_ID = "PARTITION_ID";

    public static final String DEFAULT_PARTITION_ID = "0";

    public static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

    public static final String PAYLOAD_COLUMN = "Event";

}
