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
package org.talend.components.adlsgen2.common.connection;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.configuration.action.Suggestable;
import org.talend.sdk.component.api.configuration.constraint.Required;
import org.talend.sdk.component.api.configuration.ui.layout.GridLayout;
import org.talend.sdk.component.api.configuration.ui.widget.Credential;
import org.talend.sdk.component.api.meta.Documentation;

import java.io.Serializable;

@Version(1)
@Data
@NoArgsConstructor
@AllArgsConstructor
@GridLayout({ @GridLayout.Row("accountName"), @GridLayout.Row("accountKey") })
public class AdlsGen2Connection implements Serializable {

    @Option
    @Documentation("Name of the storage account you need to access. "
            + "A storage account name can be found in the Storage accounts dashboard of the Microsoft Azure Storage system to be used. "
            + "Ensure that the administrator of the system has granted you the appropriate access permissions to this storage account.")
    private String accountName;

    @Option
    @Documentation("Storage Shared Key")
    @Credential
    private String accountKey;

    public String apiUrl() {
        return String.format(Constants.DFS_URL, getAccountName());
        // return String.format("https://%s.blob.core.windows.net", getAccountName());
    }

}
