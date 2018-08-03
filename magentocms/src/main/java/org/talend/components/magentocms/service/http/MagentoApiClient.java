package org.talend.components.magentocms.service.http;

import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.service.http.*;

import javax.json.JsonObject;
import java.util.ArrayList;
import java.util.List;

public interface MagentoApiClient extends HttpClient {

    String HEADER_Authorization = "Authorization";

    String HEADER_Content_Type = "Content-Type";

    @Request
    // @Codec(decoder = { InvalidContentDecoder.class })
    @Documentation("read record from the table according to the data set definition")
    Response<JsonObject> get(@Header(HEADER_Authorization) String auth);

    default List<JsonObject> getRecords(String auth) {
        final Response<JsonObject> resp = get(auth);
        if (resp.status() != 200) {
            throw new HttpException(resp);
        }

        List<JsonObject> dataList = new ArrayList<>();
        dataList.add(resp.body());
        return dataList;
    }

    @Request
    // @Codec(decoder = { InvalidContentDecoder.class })
    @Documentation("read record from the table according to the data set definition")
    Response<JsonObject> post(@Header(HEADER_Authorization) String auth, @Header(HEADER_Content_Type) String contentType,
            JsonObject record);

    default JsonObject postRecords(String auth, JsonObject dataList) {
        final Response<JsonObject> resp = post(auth, "application/json", dataList);
        if (resp.status() != 200) {
            throw new HttpException(resp);
        }
        return resp.body();
    }
}