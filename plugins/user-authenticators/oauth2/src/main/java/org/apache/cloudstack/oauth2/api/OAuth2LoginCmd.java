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
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.cloudstack.api.APICommand;
import org.apache.cloudstack.api.ApiConstants;
import org.apache.cloudstack.api.ApiServerService;
import org.apache.cloudstack.api.BaseCmd;
import org.apache.cloudstack.api.ApiErrorCode;
import org.apache.cloudstack.api.ResponseObject;
import org.apache.cloudstack.api.ServerApiException;
import org.apache.cloudstack.api.auth.APIAuthenticationType;
import org.apache.cloudstack.api.auth.APIAuthenticator;
import org.apache.cloudstack.api.auth.PluggableAPIAuthenticator;
import org.apache.cloudstack.oauth2.OAuth2Manager;
import org.apache.cloudstack.oauth2.response.OAuth2UrlResponse;
import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;

import com.cloud.api.response.ApiResponseSerializer;
import com.cloud.domain.DomainVO;
import com.cloud.exception.CloudAuthenticationException;
import com.cloud.user.Account;
import com.cloud.user.AccountManager;
import com.cloud.user.DomainManager;
import com.cloud.user.UserAccount;
import com.cloud.user.UserVO;

@APICommand(name = "oAuth2Login", description = "Authenticates using OAuth2", responseObject = OAuth2UrlResponse.class, since = "4.5.0")
public class OAuth2LoginCmd extends BaseCmd implements APIAuthenticator {
    public static final Logger s_logger = Logger.getLogger(OAuth2LoginCmd.class.getName());

    private static final String s_name = "oauth2urlresponse";

    @Inject
    ApiServerService _apiServer;

    OAuth2Manager _oauth2Manager;

    @Inject
    AccountManager _accountMgr;

    @Inject
    DomainManager _domainMgr;

    @Override
    public void execute() throws ServerApiException {
        // We should never reach here
        throw new ServerApiException(ApiErrorCode.METHOD_NOT_ALLOWED, "This is an authentication api, cannot be used directly");
    }

    protected ResponseObject loginUser(HttpSession session, UserAccount userAcct, Long domainId, String loginIpAddress, Map<String, Object[]> requestParameters)
            throws CloudAuthenticationException {
        if (userAcct != null) {
            String timezone = userAcct.getTimezone();
            float offsetInHrs = 0f;
            if (timezone != null) {
                TimeZone t = TimeZone.getTimeZone(timezone);
                s_logger.info("Current user logged in under " + timezone + " timezone");

                java.util.Date date = new java.util.Date();
                long longDate = date.getTime();
                float offsetInMs = (t.getOffset(longDate));
                offsetInHrs = offsetInMs / (1000 * 60 * 60);
                s_logger.info("Timezone offset from UTC is: " + offsetInHrs);
            }

            Account account = _accountMgr.getAccount(userAcct.getAccountId());

            // set the userId and account object for everyone
            session.setAttribute("userid", userAcct.getId());
            UserVO user = (UserVO)_accountMgr.getActiveUser(userAcct.getId());
            if (user.getUuid() != null) {
                session.setAttribute("user_UUID", user.getUuid());
            }

            session.setAttribute("username", userAcct.getUsername());
            session.setAttribute("firstname", userAcct.getFirstname());
            session.setAttribute("lastname", userAcct.getLastname());
            session.setAttribute("accountobj", account);
            session.setAttribute("account", account.getAccountName());

            session.setAttribute("domainid", account.getDomainId());
            DomainVO domain = (DomainVO)_domainMgr.getDomain(account.getDomainId());
            if (domain.getUuid() != null) {
                session.setAttribute("domain_UUID", domain.getUuid());
            }

            session.setAttribute("type", Short.valueOf(account.getType()).toString());
            session.setAttribute("registrationtoken", userAcct.getRegistrationToken());
            session.setAttribute("registered", new Boolean(userAcct.isRegistered()).toString());

            if (timezone != null) {
                session.setAttribute("timezone", timezone);
                session.setAttribute("timezoneoffset", Float.valueOf(offsetInHrs).toString());
            }

            // (bug 5483) generate a session key that the user must submit on every request to prevent CSRF, add that
            // to the login response so that session-based authenticators know to send the key back
            SecureRandom sesssionKeyRandom = new SecureRandom();
            byte sessionKeyBytes[] = new byte[20];
            sesssionKeyRandom.nextBytes(sessionKeyBytes);
            final String sessionKey = Base64.encodeBase64URLSafeString(sessionKeyBytes);
            session.setAttribute(ApiConstants.SESSIONKEY, sessionKey);

            return _responseGenerator.createLoginResponse(session);
        }
        throw new CloudAuthenticationException("Failed to authenticate user " + (userAcct == null ? "(null)" : userAcct.getUsername()) + " in domain " + domainId
                + "; please provide valid credentials");
    }

    @Override
    public String authenticate(String command, Map<String, Object[]> params,
                               HttpSession session, InetAddress remoteAddress, String responseType,
                               StringBuilder auditTrailSb, final HttpServletRequest req, final HttpServletResponse resp) throws ServerApiException {
        // FIXME Put string constants in ApiConstants
        if (params.containsKey("code") && params.containsKey("redirect_uri")) {
            String code = String.valueOf(params.get("code")[0]);
            String redirectUri = String.valueOf(params.get("redirect_uri")[0]);
            try {
                UserAccount userAcc = _oauth2Manager.authenticate(code, redirectUri);
                if (_apiServer.verifyUser(userAcc.getId())) {
                    return ApiResponseSerializer.toSerializedString(loginUser(session, userAcc, userAcc.getDomainId(), remoteAddress.getHostAddress(), params), responseType);
                }
                throw new ServerApiException(ApiErrorCode.PARAM_ERROR, "Unable to authenticate or retrieve user while performing OAuth2 based SSO");
            } catch (final CloudAuthenticationException ex) {
                s_logger.error("Unable to authenticate user using OAuth2 plugin: " + ex.getLocalizedMessage());
                throw new ServerApiException(ApiErrorCode.ACCOUNT_ERROR, _apiServer.getSerializedApiError(ApiErrorCode.ACCOUNT_ERROR.getHttpCode(), ex.getLocalizedMessage(),
                        params, responseType));
            }
        }
        throw new ServerApiException(ApiErrorCode.PARAM_ERROR, "Invalid call to OAuth2 Command");
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
