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
package org.talend.components.adlsgen2.migration;

import java.util.Map;

import org.talend.sdk.component.api.component.MigrationHandler;

public class ADLSMigrationHandler implements MigrationHandler {

    @Override
    public Map<String, String> migrate(int incomingVersion, Map<String, String> incomingData) {
        if (incomingVersion == 1) {
            // remap old values:
            String oldRecordSeparatorValue = incomingData.get("configuration.dataSet.csvConfiguration.recordSeparator");
            String customRecordSeparatorValue = incomingData.get("configuration.dataSet.csvConfiguration.customRecordSeparator");
            String oldFieldDelimiterValue = incomingData.get("configuration.dataSet.csvConfiguration.fieldDelimiter");
            String customFieldDelimiter = incomingData.get("configuration.dataSet.csvConfiguration.customFieldDelimiter");
            String textEnclosureCharacter = incomingData.get("configuration.dataSet.csvConfiguration.textEnclosureCharacter");
            String escapeCharacter = incomingData.get("configuration.dataSet.csvConfiguration.escapeCharacter");
            String oldFileEncodingValue = incomingData.get("configuration.dataSet.csvConfiguration.fileEncoding");
            String customFileEncoding = incomingData.get("configuration.dataSet.csvConfiguration.customFileEncoding");
            String csvSchema = incomingData.get("configuration.dataSet.csvConfiguration.csvSchema");
            String wasUseHeader = incomingData.get("configuration.dataSet.csvConfiguration.header");

            incomingData.put("configuration.dataSet.csvConfiguration.csvFormatOptions.recordDelimiter", oldRecordSeparatorValue);
            incomingData.put("configuration.dataSet.csvConfiguration.csvFormatOptions.customRecordDelimiter",
                    customRecordSeparatorValue);
            incomingData.put("configuration.dataSet.csvConfiguration.csvFormatOptions.fieldDelimiter", oldFieldDelimiterValue);
            incomingData.put("configuration.dataSet.csvConfiguration.csvFormatOptions.customFieldDelimiter",
                    customFieldDelimiter);
            incomingData.put("configuration.dataSet.csvConfiguration.csvFormatOptions.textEnclosureCharacter",
                    textEnclosureCharacter);
            incomingData.put("configuration.dataSet.csvConfiguration.csvFormatOptions.escapeCharacter", escapeCharacter);
            incomingData.put("configuration.dataSet.csvConfiguration.csvFormatOptions.encoding", oldFileEncodingValue);
            incomingData.put("configuration.dataSet.csvConfiguration.csvFormatOptions.customEncoding", customFileEncoding);
            incomingData.put("configuration.dataSet.csvConfiguration.csvFormatOptions.useHeader", wasUseHeader); // no need to set
                                                                                                                 // header, 1 is
                                                                                                                 // default value

            incomingData.put("configuration.dataSet.csvConfiguration.csvSchema", csvSchema);
        }
        return incomingData;
    }
}
