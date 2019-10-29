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
package org.talend.components.rest.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.talend.components.common.text.Substitutor;
import org.talend.components.rest.configuration.HttpMethod;
import org.talend.components.rest.configuration.Param;
import org.talend.components.rest.configuration.RequestConfig;
import org.talend.sdk.component.api.record.Record;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.record.RecordBuilderFactory;
import org.talend.sdk.component.junit.BaseComponentsHandler;
import org.talend.sdk.component.junit5.Injected;
import org.talend.sdk.component.junit5.WithComponents;
import org.talend.sdk.component.runtime.manager.service.RecordPointerFactoryImpl;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Slf4j
@WithComponents(value = "org.talend.components.rest")
public class RestServiceTest {

    @Service
    RestService service;

    @Service
    protected RecordBuilderFactory recordBuilderFactory;

    @Injected
    private BaseComponentsHandler handler;

    private RequestConfig config;

    @BeforeEach
    void buildConfig() {
        // Inject needed services
        handler.injectServices(this);
        config = RequestConfigBuilderTest.getEmptyRequestConfig();
    }

    @Test
    void setPathParams() {
        config.getDataset().getDatastore().setBase("");
        config.getDataset().getDatastore().setConnectionTimeout(5000);
        config.getDataset().getDatastore().setReadTimeout(5000);
        config.getDataset().setResource("get/{resource}/{id}/{field}/id/{id}/resource/{resource}/end");
        config.getDataset().setMethodType(HttpMethod.GET);

        config.getDataset().setHasQueryParams(false);
        config.getDataset().setHasHeaders(false);

        List<String[]> paramList = new ArrayList<>();
        paramList.add(new String[] { "leads", "124", "name" });
        paramList.add(new String[] { "{leads}", "{124}", "{name}" });

        for (String[] params : paramList) {
            List<Param> pathParams = new ArrayList<>();
            pathParams.add(new Param("resource", params[0]));
            pathParams.add(new Param("id", params[1]));
            pathParams.add(new Param("field", params[2]));
            config.getDataset().setHasPathParams(true);
            config.getDataset().setPathParams(pathParams);

            String finalResource = service.setPathParams(config.getDataset().getResource(),
                    config.getDataset().getHasPathParams(), config.pathParams());

            assertEquals("get/" + params[0] + "/" + params[1] + "/" + params[2] + "/id/" + params[1] + "/resource/" + params[0]
                    + "/end", finalResource);
        }
    }

    @Test
    void setParamsFromRecords() {
        int id = 150;
        String name = "paco";
        ZonedDateTime now = ZonedDateTime.now();

        Record record = recordBuilderFactory.newRecordBuilder() //
                .withInt("id", id) //
                .withString("name", name) //
                .withDateTime("date", now) //
                .withString("unused", "unused") //
                .build();

        List<Param> queryParams = new ArrayList<>();
        queryParams.add(new Param("id", "${/id}"));
        queryParams.add(new Param("name", "<name>${/name}</name>"));
        queryParams.add(new Param("complexe1", "<name>${/name}</name><id>${/id}</id>"));
        queryParams.add(new Param("complexe2", "<name>${/name}</name><id>${/id}</id><unexists>${/unexists:-default}</unexists>"));
        queryParams.add(new Param("complexe3",
                "<name>${/name}</name><id>${/id}</id><unexists>${/unexists:-default}</unexists><escaped>\\${escaped:-none}<escaped>"));

        config.getDataset().setHasQueryParams(true);
        config.getDataset().setQueryParams(queryParams);

        Substitutor substitutor = new RecordSubstitutor("${", "}", record, new RecordPointerFactoryImpl(null));

        Map<String, String> updatedQueryParams = service.updateParamsFromRecord(config.queryParams(), substitutor);

        assertEquals("" + id, updatedQueryParams.get("id"));
        assertEquals("<name>" + name + "</name>", updatedQueryParams.get("name"));
        assertEquals("<name>" + name + "</name><id>" + id + "</id>", updatedQueryParams.get("complexe1"));
        assertEquals("<name>" + name + "</name><id>" + id + "</id><unexists>default</unexists>",
                updatedQueryParams.get("complexe2"));
        assertEquals("<name>" + name + "</name><id>" + id + "</id><unexists>default</unexists><escaped>${escaped:-none}<escaped>",
                updatedQueryParams.get("complexe3"));
    }

}
