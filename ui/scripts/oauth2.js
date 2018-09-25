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

(function($, cloudStack, window) {
    $.extend(cloudStack, {

        OAuth2: {
            authenticate: function(loginArgs, code, redirectUri) {
                var haveSuccess = false, self = this;
                // remove parameter code from url
                history.replaceState(null, null, window.location.origin + window.location.pathname);

                $.ajax({
                    url: clientApiUrl + '?command=oAuth2Login&response=json&code=' + code + '&redirect_uri=' + escape(redirectUri),
                    dataType: "json",
                    async: false,
                    success: function(json) {
                        if (loginArgs.processLogin(json.loginresponse)) {
                            g_loginResponse = json.loginresponse;
                            var bypass = loginArgs.bypassLoginCheck();
                            self.postAuthentication(loginArgs, bypass);
                            haveSuccess = true;
                        }
                    },
                    error: function(jqXHR) {
                        var notice;
                        if (jqXHR.status == 531 || jqXHR.status == 431) {
                            var error = $.parseJSON(jqXHR.responseText);
                            notice = error.oauth2urlresponse.errortext;
                        } else {
                            notice = "Error authenticating with OAuth2. Use login/password credentials";
                        }
                        cloudStack.dialog.notice({
                            message: notice,
                            clickAction: onLogoutCallback
                        });
                    },
                    beforeSend: function(XMLHttpRequest) {
                        return true;
                    }
                });
                return haveSuccess;
            },

            redirectUser: function() {
                var haveSuccess = false;
                // check if user is already logged

                // don't put querystring in url
                var redirectUri = window.location.origin + window.location.pathname;
                $.cookie('oauth_redirect', redirectUri);
                $.ajax({
                    url: clientApiUrl + "?command=oauthRedirect&response=json&redirect_uri=" + escape(redirectUri),
                    dataType: "json",
                    async: false,
                    success: function(json) {
                        // to ensure never will break
                        redirectUri = json && json.oauth2urlresponse &&
                            json.oauth2urlresponse.authenticationurl && json.oauth2urlresponse.authenticationurl.redirectUri;
                        if (redirectUri) {
                            // 
                            haveSuccess = true;
                            $.cookie('logout_redirect', json.oauth2urlresponse.authenticationurl.logoutUri);
                            window.location.assign(redirectUri);
                        }
                    },
                    beforeSend: function(XMLHttpRequest) {
                        return true;
                    }
                });
                return haveSuccess;
            },

            postAuthentication: function(loginArgs, bypass) {
                var logoutUrl = $.cookie('logout_redirect');
                $.cookie('logout_redirect', null); // remove cookie
                var old_onLogoutCallback = onLogoutCallback;
                onLogoutCallback = function() {
                    old_onLogoutCallback();
                    if (logoutUrl) {
                        window.location.assign(logoutUrl);
                        return false;
                    } else {
                        return true;
                    }
                };

                loginArgs.complete({
                    user: bypass.user
                });
                $(window).trigger('cloudStack.init');
            },

            login: function(loginArgs) {
                var code = $.urlParam("code"),
                    redirectUri = $.cookie('oauth_redirect'),
                    bypass;
                if (code && redirectUri) {
                    // if there are code and redirectUri, make OAuth 2 authentication
                    return this.authenticate(loginArgs, code, redirectUri);
                } else {
                    // otherwise, check if already exists an authentication (other window)
                    bypass = loginArgs.bypassLoginCheck();
                    if (bypass) {
                        this.postAuthentication(loginArgs, bypass);
                        return true;
                    } else {
                        // to avoid double call to bypassLoginCheck, set it to null
                        loginArgs.bypassLoginCheck = null;
                        // redirect user to authentication
                        return this.redirectUser();
                    }
                }
            }
        }
    });
}($, cloudStack, window));
