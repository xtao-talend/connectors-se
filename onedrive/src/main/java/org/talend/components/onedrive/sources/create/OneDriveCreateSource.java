package org.talend.components.onedrive.sources.create;

import com.microsoft.graph.models.extensions.DriveItem;
import lombok.extern.slf4j.Slf4j;
import org.talend.components.onedrive.helpers.ConfigurationHelper;
import org.talend.components.onedrive.service.configuration.ConfigurationServiceCreate;
import org.talend.components.onedrive.service.graphclient.GraphClientService;
import org.talend.components.onedrive.service.http.BadCredentialsException;
import org.talend.components.onedrive.service.http.OneDriveAuthHttpClientService;
import org.talend.components.onedrive.service.http.OneDriveHttpClientService;
import org.talend.sdk.component.api.component.Icon;
import org.talend.sdk.component.api.component.Version;
import org.talend.sdk.component.api.configuration.Option;
import org.talend.sdk.component.api.meta.Documentation;
import org.talend.sdk.component.api.processor.*;

import javax.json.JsonObject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Version(1)
// @Icon(Icon.IconType.STAR)
@Icon(value = Icon.IconType.CUSTOM, custom = "onedrive_create")
@Processor(name = "Create")
@Documentation("Data create processor")
public class OneDriveCreateSource implements Serializable {

    private final OneDriveCreateConfiguration configuration;

    private OneDriveHttpClientService oneDriveHttpClientService;

    private GraphClientService graphClientService;

    private List<JsonObject> batchData = new ArrayList<>();

    public OneDriveCreateSource(@Option("configuration") final OneDriveCreateConfiguration configuration,
            final OneDriveHttpClientService oneDriveHttpClientService,
            final OneDriveAuthHttpClientService oneDriveAuthHttpClientService,
            ConfigurationServiceCreate configurationServiceCreate, GraphClientService graphClientService) {
        this.configuration = configuration;
        this.oneDriveHttpClientService = oneDriveHttpClientService;
        this.graphClientService = graphClientService;
        ConfigurationHelper.setupServicesCreate(configuration, configurationServiceCreate, oneDriveAuthHttpClientService);
    }

    @ElementListener
    public void onNext(@Input final JsonObject record, final @Output OutputEmitter<JsonObject> success,
            final @Output("reject") OutputEmitter<Reject> reject) {
        processOutputElement(record, success, reject);
    }

    private void processOutputElement(final JsonObject record, OutputEmitter<JsonObject> success, OutputEmitter<Reject> reject) {
        String parentId = record.getString("parentId");
        try {
            DriveItem newItem = oneDriveHttpClientService.createItem(parentId, configuration.getObjectType(),
                    configuration.getObjectPath());
            JsonObject newRecord = graphClientService.driveItemToJsonObject(newItem);
            success.emit(newRecord);
        } catch (BadCredentialsException e) {
            log.error(e.getMessage());
        } catch (Exception e) {
            log.warn(e.getMessage());
            reject.emit(new Reject(e.getMessage(), record));
        }
    }
}