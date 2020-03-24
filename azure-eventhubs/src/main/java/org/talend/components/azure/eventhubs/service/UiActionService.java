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
package org.talend.components.azure.eventhubs.service;

import static com.azure.messaging.eventhubs.implementation.ClientConstants.ENDPOINT_FORMAT;
import static com.azure.storage.common.implementation.Constants.ConnectionStringConstants.BLOB_ENDPOINT_NAME;
import static org.talend.components.azure.common.service.AzureComponentServices.SAS_PATTERN;
import static org.talend.components.azure.eventhubs.common.AzureEventHubsConstant.DEFAULT_DOMAIN_NAME;

import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.talend.components.azure.common.connection.AzureStorageConnectionAccount;
import org.talend.components.azure.common.service.AzureComponentServices;
import org.talend.components.azure.datastore.AzureCloudConnection;
import org.talend.components.azure.eventhubs.datastore.AzureEventHubsDataStore;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.asyncvalidation.AsyncValidation;
import org.talend.sdk.component.api.service.asyncvalidation.ValidationResult;
import org.talend.sdk.component.api.service.completion.DynamicValues;
import org.talend.sdk.component.api.service.completion.Values;
import org.talend.sdk.component.api.service.healthcheck.HealthCheck;
import org.talend.sdk.component.api.service.healthcheck.HealthCheckStatus;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubConsumerClient;
import com.azure.storage.blob.BlobContainerAsyncClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.common.StorageSharedKeyCredential;
import com.microsoft.azure.storage.CloudStorageAccount;

import lombok.Getter;

@Service
public class UiActionService {

    @Getter
    @Service
    AzureComponentServices connectionService;

    @HealthCheck("checkEndpoint")
    public HealthCheckStatus checkEndpoint(@Option final AzureEventHubsDataStore conn, final Messages i18n) {
        EventHubConsumerClient ehClient = null;
        try {

            String endpoint = null;
            if (conn.isSpecifyEndpoint()) {
                endpoint = conn.getEndpoint();//
            } else {
                endpoint = String.format(Locale.US, ENDPOINT_FORMAT, conn.getNamespace(), DEFAULT_DOMAIN_NAME);
            }
            String ehConnString = String.format("Endpoint=%s;SharedAccessKeyName=%s;SharedAccessKey=%s;EntityPath=%s", endpoint,
                    conn.getSasKeyName(), conn.getSasKey(), "fakeEventhub");

            ehClient = new EventHubClientBuilder().connectionString(ehConnString).consumerGroup("fakeGroup")
                    .retry(new AmqpRetryOptions().setTryTimeout(Duration.ofSeconds(10))).shareConnection().buildConsumerClient();
            ehClient.getEventHubProperties();
        } catch (Throwable exception) {
            // TODO which kind of error i
            String errorMessage = exception.getMessage();
            if (errorMessage.contains(String.valueOf(10 * 1000))) {
                // errorMessage = "invalid endpoint or network issue!";
                return new HealthCheckStatus(HealthCheckStatus.Status.KO, i18n.healthCheckFailed(errorMessage));
            }
        } finally {
            if (ehClient != null) {
                ehClient.close();
            }
        }
        return new HealthCheckStatus(HealthCheckStatus.Status.OK, i18n.healthCheckOk());
    }

    @AsyncValidation("checkEventHub")
    public ValidationResult checkEventHub(@Option final AzureEventHubsDataStore connection, @Option final String eventHubName,
            final Messages i18n) {
        ValidationResult result = new ValidationResult();
        try {
            getPartitionIds(connection, eventHubName, new AmqpRetryOptions().setTryTimeout(Duration.ofSeconds(10)));
        } catch (Throwable exception) {
            String koComment = exception.getMessage();
            result.setStatus(ValidationResult.Status.KO);
            result.setComment(koComment);
            return result;
        }
        result.setStatus(ValidationResult.Status.OK);
        result.setComment("EventHub is available!");
        return result;
    }

    // This INCOMING_PATHS_DYNAMIC service is a flag for inject incoming paths dynamic, won't be called
    @DynamicValues("INCOMING_PATHS_DYNAMIC")
    public Values actions() {
        return new Values(Collections.EMPTY_LIST);
    }

    public CloudStorageAccount createStorageAccount(AzureCloudConnection azureConnection) throws URISyntaxException {
        return azureConnection.isUseAzureSharedSignature()
                ? connectionService.createStorageAccount(azureConnection.getSignatureConnection())
                : connectionService.createStorageAccount(azureConnection.getAccountConnection(),
                        azureConnection.getEndpointSuffix());
    }

    public BlobContainerAsyncClient createBlobContainerAsyncClient(AzureCloudConnection azureConnection, String containerName)
            throws URISyntaxException {

        CloudStorageAccount cloudAccount = createStorageAccount(azureConnection);
        BlobContainerClientBuilder clientBuilder = new BlobContainerClientBuilder();
        String storageConnectionString = String.format("%s=%s", BLOB_ENDPOINT_NAME, cloudAccount.getBlobEndpoint().toString());
        clientBuilder.connectionString(storageConnectionString);
        clientBuilder.containerName(containerName);
        if (azureConnection.isUseAzureSharedSignature()) {
            String sasTokenURL = azureConnection.getSignatureConnection().getAzureSharedAccessSignature();
            Matcher matcher = Pattern.compile(SAS_PATTERN).matcher(sasTokenURL);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalidated sas URL!!!");
            }
            clientBuilder.sasToken(matcher.group(5));
        } else {
            AzureStorageConnectionAccount accountConnection = azureConnection.getAccountConnection();
            StorageSharedKeyCredential credential = new StorageSharedKeyCredential(accountConnection.getAccountName(),
                    accountConnection.getAccountKey());
            clientBuilder.credential(credential);
        }
        BlobContainerAsyncClient blobContainerAsyncClient = clientBuilder.buildAsyncClient();
        return blobContainerAsyncClient;
    }

    public List<String> getPartitionIds(AzureEventHubsDataStore connection, String eventHubName, AmqpRetryOptions retryOptions) {
        EventHubConsumerClient ehClient = null;
        try {
            String endpoint = null;
            if (connection.isSpecifyEndpoint()) {
                endpoint = connection.getEndpoint();//
            } else {
                endpoint = String.format(Locale.US, ENDPOINT_FORMAT, connection.getNamespace(), DEFAULT_DOMAIN_NAME);
            }
            String ehConnString = String.format("Endpoint=%s;SharedAccessKeyName=%s;SharedAccessKey=%s;EntityPath=%s", endpoint,
                    connection.getSasKeyName(), connection.getSasKey(), eventHubName);

            ehClient = new EventHubClientBuilder().connectionString(ehConnString).consumerGroup("$Default").retry(retryOptions)
                    .shareConnection().buildConsumerClient();
            List<String> partitionIds = new ArrayList<>();
            for (String partitionId : ehClient.getPartitionIds()) {
                partitionIds.add(partitionId);
            }
            return partitionIds;
        } catch (Throwable throwable) {
            throw throwable;
        } finally {
            if (ehClient != null) {
                ehClient.close();
            }
        }
    }

}