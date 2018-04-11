// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package org.apache.cloudstack.oauth2.api;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiServerService;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.Parameter;
import org.apache.cloudstack.api.auth.APIAuthenticationType;
import org.apache.cloudstack.api.auth.APIAuthenticator;
import org.apache.cloudstack.api.auth.PluggableAPIAuthenticator;
import org.apache.cloudstack.oauth2.OAuth2Manager;
import org.apache.cloudstack.oauth2.response.OAuth2UrlResponse;
import org.apache.log4j.Logger;

import com.cloud.api.response.ApiResponseSerializer;
import com.cloud.user.Account;
import com.cloud.utils.HttpUtils;

@APICommand(name = "oauthRedirect", description = "Return redirect url for OAuth2 SSO", requestHasSensitiveInfo = false, responseHasSensitiveInfo = false, responseObject = OAuth2UrlResponse.class, since = "4.5.0")
public class OAuth2RedirectCmd extends BaseCmd implements APIAuthenticator {
    public static final Logger s_logger = Logger.getLogger(OAuth2RedirectCmd.class.getName());

    private static final String s_name = "oauth2urlresponse";

    @Inject
    ApiServerService _apiServer;

    OAuth2Manager _oauth2Manager;

    @Parameter(name = "redirect_uri", type = CommandType.STRING, required = false, description = "URL the user is returned to")
    private String redirectUri;

    @Override
    public void execute() throws ServerApiException {
        // We should never reach here
        throw new ServerApiException(ApiErrorCode.METHOD_NOT_ALLOWED, "This is an authentication api, cannot be used directly");
    }

    @Override
    public String authenticate(String command, Map<String, Object[]> params,
                               HttpSession session, InetAddress remoteAddress, String responseType,
                               StringBuilder auditTrailSb, final HttpServletRequest req, final HttpServletResponse resp) throws ServerApiException {
        // build redirect url
        Object[] redirectUriObj = params.get("redirect_uri");
        String redirectUri = null;
        if (redirectUriObj != null && redirectUriObj.length > 0) {
            redirectUri = (String)redirectUriObj[0];
        }
        String url = _oauth2Manager.generateAuthenticationUrl(redirectUri);
        String logoutUrl = _oauth2Manager.getLogoutUrlWithProvider();
        OAuth2UrlResponse response = new OAuth2UrlResponse();
        response.setRedirectUri(url);
        response.setLogoutUri(logoutUrl);
        response.setObjectName("authenticationurl");
        response.setResponseName(getCommandName());
        return ApiResponseSerializer.toSerializedString(response, HttpUtils.RESPONSE_TYPE_JSON);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_TYPE_NORMAL;
    }


    @Override
    public APIAuthenticationType getAPIType() {
        return APIAuthenticationType.LOGIN_API;
    }

    @Override
    public void setAuthenticators(List<PluggableAPIAuthenticator> authenticators) {
        for (PluggableAPIAuthenticator authManager : authenticators) {
            if (authManager instanceof OAuth2Manager) {
                _oauth2Manager = (OAuth2Manager)authManager;
            }
        }
        if (_oauth2Manager == null) {
            s_logger.error("No suitable Pluggable Authentication Manager found for OAuth2 Login Cmd");
        }
    }

}
