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

package org.talend.components.mongodb.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.talend.components.mongodb.utils.MongoDBTestExtension;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.healthcheck.HealthCheckStatus;
import org.talend.sdk.component.junit5.WithComponents;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WithComponents("org.talend.components.mongodb")
@ExtendWith(MongoDBTestExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class UIMongoDBServiceTestIT {

    @Service
    private UIMongoDBService uiMongoDBService;

    private MongoDBTestExtension.TestContext testContext;

    @BeforeAll
    private void init(MongoDBTestExtension.TestContext testContext) {
        this.testContext = testContext;
    }

    @Test
    public void testSuccessfulConnection() {
        HealthCheckStatus status = uiMongoDBService.testConnection(testContext.getDataStore());

        assertEquals(HealthCheckStatus.Status.OK, status.getStatus());
    }

}