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
package org.talend.components.azure.eventhubs;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;

import org.talend.components.azure.eventhubs.datastore.AzureEventHubsDataStore;
import org.talend.components.azure.eventhubs.service.UiActionService;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.junit.BaseComponentsHandler;
import org.talend.sdk.component.junit5.Injected;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.maven.MavenDecrypter;
import org.talend.sdk.component.maven.Server;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.azure.storage.common.sas.AccountSasPermission;
import com.azure.storage.common.sas.AccountSasResourceType;
import com.azure.storage.common.sas.AccountSasService;
import com.azure.storage.common.sas.AccountSasSignatureValues;

import lombok.Data;

@Data
@WithComponents("org.talend.components.azure.eventhubs")
public class AzureEventHubsTestBase implements Serializable {

    public static String ENDPOINT = "sb://comptest.servicebus.windows.net";

    public static String SASKEY_NAME;

    public static String SASKEY;

    protected static final String SHARED_EVENTHUB_NAME = "eh-junit-shared";

    protected static final String CONSUME_GROUP = "consumer-1";

    protected static final String ACCOUNT_NAME;

    protected static final String ACCOUNT_KEY;

    public static final String EH_CONNECTION_PATTERN = "DefaultEndpointsProtocol=https;AccountName=%s;AccountKey=%s;EndpointSuffix=core.windows.net";

    public static final String SAS_URI_PATTERN = "https://%s.blob.core.windows.net/?%s";

    protected static final String SAS_TOKEN;

    @Service
    UiActionService service;

    static {
        final MavenDecrypter decrypter = new MavenDecrypter();
        final Server serverSaskey = decrypter.find("azure-eventhubs-saskey");
        SASKEY_NAME = serverSaskey.getUsername();
        SASKEY = serverSaskey.getPassword();

        final Server storageAccount = decrypter.find("azure-storage-account");
        ACCOUNT_NAME = storageAccount.getUsername();
        ACCOUNT_KEY = storageAccount.getPassword();

        SAS_TOKEN = getSasToken();

    }

    @Injected
    private BaseComponentsHandler componentsHandler;

    public AzureEventHubsDataStore getDataStore() {
        AzureEventHubsDataStore dataStore = new AzureEventHubsDataStore();
        dataStore.setSpecifyEndpoint(true);
        dataStore.setEndpoint(ENDPOINT);
        dataStore.setAuthMethod(AzureEventHubsDataStore.AuthMethod.SAS);
        dataStore.setSasKeyName(SASKEY_NAME);
        dataStore.setSasKey(SASKEY);
        return dataStore;
    }

    protected String getUniqueID() {
        return Integer.toString(ThreadLocalRandom.current().nextInt(1, 100000));
    }

    public static String getSasToken() {

        String connectionString = String.format(EH_CONNECTION_PATTERN, ACCOUNT_NAME, ACCOUNT_KEY);
        // BEGIN: com.azure.storage.common.StorageSharedKeyCredential.fromConnectionString#String
        StorageSharedKeyCredential credential = StorageSharedKeyCredential.fromConnectionString(connectionString);
        // END: com.azure.storage.common.StorageSharedKeyCredential.fromConnectionString#String
        BlobServiceClient client = new BlobServiceClientBuilder().credential(credential).buildClient();

        OffsetDateTime myExpiryTime = OffsetDateTime.now().plusDays(1);
        AccountSasPermission permissions = new AccountSasPermission() // Need to check whether those permissions all needed.
                .setAddPermission(true) //
                .setDeletePermission(true) //
                .setListPermission(true) //
                .setProcessMessages(true) //
                .setReadPermission(true) //
                .setUpdatePermission(true) //
                .setWritePermission(true);
        AccountSasService service = new AccountSasService().setBlobAccess(true); //

        AccountSasResourceType resourceType = new AccountSasResourceType().setContainer(true) //
                .setService(true) //
                .setObject(true); //

        AccountSasSignatureValues blobServiceSasSignatureValues = new AccountSasSignatureValues(myExpiryTime, permissions,
                service, resourceType).setStartTime(OffsetDateTime.now());
        String sasToken = client.generateAccountSas(blobServiceSasSignatureValues);
        String tokenURL = String.format(SAS_URI_PATTERN, ACCOUNT_NAME, sasToken);
        return tokenURL;
    }

}