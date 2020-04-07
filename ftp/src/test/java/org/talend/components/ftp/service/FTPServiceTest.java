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
package org.talend.components.ftp.service;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.talend.components.ftp.dataset.FTPDataSet;
import org.talend.components.ftp.datastore.FTPDataStore;
import org.talend.components.ftp.jupiter.FtpFile;
import org.talend.components.ftp.jupiter.FtpServer;
import org.talend.sdk.component.api.service.Service;
import org.talend.sdk.component.api.service.injector.Injector;
import org.talend.sdk.component.junit5.WithComponents;

@FtpFile(base = "fakeFTP/", port = 4528)
@WithComponents(value = "org.talend.components.ftp")
public class FTPServiceTest {

    @Service
    Injector injector;

    @Test
    public void testPathIsFile() {
        FTPDataStore datastore = new FTPDataStore();
        datastore.setHost("localhost");
        datastore.setUseCredentials(true);
        datastore.setUsername(FtpServer.USER);
        datastore.setPassword(FtpServer.PASSWD);
        datastore.setPort(4528);

        FTPDataSet dataset = new FTPDataSet();
        dataset.setDatastore(datastore);
        dataset.setPath("/communes");

        FTPService ftpService = new FTPService();
        injector.inject(ftpService);
        Assertions.assertFalse(ftpService.pathIsFile(dataset), "/communes is not a file.");

        dataset.setPath("/communes/communes_0.csv");
        Assertions.assertTrue(ftpService.pathIsFile(dataset), "/communes/communes_0.csv is a file.");
    }

}
