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
package org.talend.components.azure.eventhubs.runtime.converters;

import javax.json.JsonBuilderFactory;
import javax.json.JsonReaderFactory;
import javax.json.bind.Jsonb;
import javax.json.spi.JsonProvider;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.talend.components.azure.eventhubs.service.Messages;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit5.WithComponents;

@WithComponents("org.talend.components.azure.eventhubs")
class JsonConverterTest {

    @Service
    public RecordBuilderFactory recordBuilderFactory;

    @Service
    private JsonBuilderFactory jsonBuilderFactory;

    @Service
    private JsonProvider jsonProvider;

    @Service
    private JsonReaderFactory readerFactory;

    @Service
    private Jsonb jsonb;

    @Service
    private Messages i18n;

    @Test
    void toRecord() {
        JsonConverter converter = JsonConverter.of(recordBuilderFactory, jsonBuilderFactory, jsonProvider, readerFactory, jsonb,
                i18n);
        final Record record = converter.toRecord("{\"ID\":\"No.00001\",\"NAME\":\"Josh\",\"AGE\":32}");
        Assert.assertEquals("No.00001", record.getString("ID"));
        Assert.assertEquals("Josh", record.getString("NAME"));
        Assert.assertEquals(32, record.getInt("AGE"));
    }

    @Test
    void fromRecord() {
        JsonConverter converter = JsonConverter.of(recordBuilderFactory, jsonBuilderFactory, jsonProvider, readerFactory, jsonb,
                i18n);
        Record.Builder recordBuilder = recordBuilderFactory.newRecordBuilder();
        recordBuilder.withString("ID", "No.00001");
        recordBuilder.withString("NAME", "Josh");
        recordBuilder.withInt("AGE", 32);
        try {
            String jsonStr = converter.fromRecord(recordBuilder.build());
            Assert.assertEquals("{\"ID\":\"No.00001\",\"NAME\":\"Josh\",\"AGE\":32}", jsonStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void checkSchema() {
        JsonConverter converter = JsonConverter.of(recordBuilderFactory, jsonBuilderFactory, jsonProvider, readerFactory, jsonb,
                i18n);
        final Record record = converter.toRecord(
                "{\"Id\": \"0012v00002Pei1WAAR\", \"IsDeleted\": false, \"MasterRecordId\": null, \"Name\": \"TestName\", "
                        + "\"Type\": null, \"ParentId\": null, \"BillingStreet\": \"123 Main Street\", \"BillingCity\": null, "
                        + "\"BillingState\": \"CA\", \"BillingPostalCode\": \"226296\", \"BillingCountry\": null, \"BillingLatitude\": null, "
                        + "\"BillingLongitude\": null, \"ShippingStreet\": \"Address2 456\", \"ShippingCity\": null, \"ShippingState\": \"CA\","
                        + " \"ShippingPostalCode\": \"7\", \"ShippingCountry\": null, \"ShippingLatitude\": null, \"ShippingLongitude\": null, "
                        + "\"Phone\": null, \"Fax\": null, \"AccountNumber\": null, \"Website\": null, \"PhotoUrl\": \"/services/images/photo/0012v00002Pei1WAAR\", "
                        + "\"Sic\": null, \"Industry\": null, \"AnnualRevenue\": null, \"NumberOfEmployees\": null, \"Ownership\": null, \"TickerSymbol\": null, "
                        + "\"Description\": null, \"Rating\": null, \"Site\": null, \"OwnerId\": \"00590000000rQ0TAAU\", \"CreatedDate\": 1563257602000, "
                        + "\"CreatedById\": \"00590000000rQ0TAAU\", \"LastModifiedDate\": 1563257602000, \"LastModifiedById\": \"00590000000rQ0TAAU\", "
                        + "\"SystemModstamp\": 1563257602000, \"LastActivityDate\": null, \"LastViewedDate\": null, \"LastReferencedDate\": null, "
                        + "\"Jigsaw\": null, \"JigsawCompanyId\": null, \"AccountSource\": null, \"SicDesc\": null, \"CustomerPriority__c\": null, "
                        + "\"SLA__c\": null, \"Active__c\": null, \"NumberofLocations__c\": null, \"UpsellOpportunity__c\": null, \"SLASerialNumber__c\": null, "
                        + "\"SLAExpirationDate__c\": null, \"copy_id__c\": null, \"test_underline__c\": null}");
    }
}