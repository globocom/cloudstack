package org.apache.cloudstack.oauth2;

import org.apache.cloudstack.api.auth.PluggableAPIAuthenticator;

import com.cloud.user.UserAccount;

public interface OAuth2Manager extends PluggableAPIAuthenticator {

    public String generateAuthenticationUrl(String redirectUri);

    public UserAccount authenticate(String code, String redirectUri);

    public String getLogoutUrlWithProvider();

}
