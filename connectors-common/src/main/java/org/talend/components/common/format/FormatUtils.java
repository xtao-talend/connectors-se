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
package org.talend.components.common.format;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.talend.components.common.format.csv.CSVFieldDelimiter;
import org.talend.components.common.format.csv.CSVFormatOptions;
import org.talend.components.common.format.csv.CSVFormatOptionsWithSchema;
import org.talend.components.common.format.csv.CSVRecordDelimiter;
import org.talend.components.common.format.excel.ExcelFormatOptions;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FormatUtils {

    public static String getUsedEncodingValue(CSVFormatOptions config) {

        if (config.getEncoding() == Encoding.OTHER) {
            try {
                Charset.forName(config.getCustomEncoding());
                return config.getCustomEncoding();
            } catch (Exception e) {
                String msg = String.format("Encoding not supported %s.", config.getCustomEncoding());
                log.error("[effectiveFileEncoding] {}", msg);
                throw new IllegalArgumentException(msg);
            }
        } else {
            return config.getEncoding().getEncodingValue();
        }
    }

    public static String getUsedEncodingValue(ExcelFormatOptions config) {
        return config.getEncoding() == Encoding.OTHER ? config.getCustomEncoding() : config.getEncoding().getEncodingValue();

    }

    public static char getFieldDelimiterValue(CSVFormatOptions config) {
        return config.getFieldDelimiter() == CSVFieldDelimiter.OTHER ? config.getCustomFieldDelimiter().charAt(0)
                : config.getFieldDelimiter().getDelimiterValue();
    }

    public static String getRecordDelimiterValue(CSVFormatOptions config) {
        return config.getRecordDelimiter() == CSVRecordDelimiter.OTHER ? config.getCustomRecordDelimiter()
                : config.getRecordDelimiter().getDelimiterValue();
    }

    public static List<String> getCsvSchemaHeaders(CSVFormatOptionsWithSchema csvFormatOptionsWithSchema) {
        List<String> headers = new ArrayList<>();
        if (StringUtils.isEmpty(csvFormatOptionsWithSchema.getCsvSchema())) {
            return headers;
        }
        try {
            return Arrays
                    .stream(csvFormatOptionsWithSchema.getCsvSchema()
                            .split(String.valueOf(getFieldDelimiterValue(csvFormatOptionsWithSchema.getCsvFormatOptions()))))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("[getCsvSchemaHeaders] Cannot get Headers from {}: {}", csvFormatOptionsWithSchema.getCsvSchema(),
                    e.getMessage());
        }
        return headers;
    }
}
