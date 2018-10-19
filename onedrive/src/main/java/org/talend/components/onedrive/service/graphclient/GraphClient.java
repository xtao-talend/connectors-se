package org.talend.components.onedrive.service.graphclient;

import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.http.IHttpRequest;
import com.microsoft.graph.logger.ILogger;
import com.microsoft.graph.logger.LoggerLevel;
import com.microsoft.graph.models.extensions.DriveItem;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.requests.extensions.GraphServiceClient;
import com.microsoft.graph.requests.extensions.IDriveRequestBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.talend.components.onedrive.common.OneDriveDataStore;
import org.talend.components.onedrive.common.UnknownAuthenticationTypeException;
import org.talend.components.onedrive.helpers.AuthorizationHelper;
import org.talend.components.onedrive.service.http.BadCredentialsException;

import java.io.IOException;

@Slf4j
public class GraphClient {

    @Getter
    private IGraphServiceClient graphServiceClient;

    @Setter
    private String accessToken;

    @Getter
    DriveItem root;

    public GraphClient(OneDriveDataStore dataStore, AuthorizationHelper authorizationHelper)
            throws BadCredentialsException, IOException, UnknownAuthenticationTypeException {
        IAuthenticationProvider authenticationProvider = new IAuthenticationProvider() {

            @Override
            public void authenticateRequest(IHttpRequest request) {
                log.debug("auth: " + accessToken);
                request.addHeader("Authorization", accessToken);
            }
        };

        ILogger logger = new ILogger() {

            @Override
            public void setLoggingLevel(LoggerLevel loggerLevel) {

            }

            @Override
            public LoggerLevel getLoggingLevel() {
                return null;
            }

            @Override
            public void logDebug(String s) {

            }

            @Override
            public void logError(String s, Throwable throwable) {

            }
        };

        graphServiceClient = GraphServiceClient.builder().authenticationProvider(authenticationProvider).logger(logger)
                .buildClient();
        setAccessToken(authorizationHelper.getAuthorization(dataStore));
        root = getDriveRequestBuilder().root().buildRequest().get();
    }

    public IDriveRequestBuilder getDriveRequestBuilder() {
        IDriveRequestBuilder driveRequestBuilder = graphServiceClient.me().drive();
        return driveRequestBuilder;
    }
}