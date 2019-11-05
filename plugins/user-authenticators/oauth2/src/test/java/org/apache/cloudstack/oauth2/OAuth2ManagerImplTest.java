package org.apache.cloudstack.oauth2;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URLEncoder;

import javax.inject.Inject;

import org.apache.cloudstack.test.utils.SpringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import com.cloud.domain.DomainVO;
import com.cloud.user.AccountManager;
import com.cloud.user.DomainManager;
import com.cloud.user.dao.UserAccountDao;
import com.cloud.utils.component.ComponentContext;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class OAuth2ManagerImplTest {

    @Inject
    private OAuth2ManagerImpl _oAuth2Auth;

    private final String AUTHLOCATION = "https://www.example.com/oauth/authorize";
    private final String CLIENTID = "djAjpUQ53oXGY1SiUGFM";
    private final String CLIENTSECRET = "jaskm9AKnmadsiFNUFNAlnk15kn";
    private final String TOKENURL = "https://www.example.com/oauth/token";

    @Before
    public void setUp() throws Exception {
        ComponentContext.initComponentsLifeCycle();
        _oAuth2Auth = spy(_oAuth2Auth);

        when(_oAuth2Auth.getAuthorizationURL()).thenReturn(AUTHLOCATION);
        when(_oAuth2Auth.getClientID()).thenReturn(CLIENTID);
        when(_oAuth2Auth.getClientSecret()).thenReturn(CLIENTSECRET);
        when(_oAuth2Auth.getTokenURL()).thenReturn(TOKENURL);
        when(_oAuth2Auth.getUserDomainVO()).thenReturn(new DomainVO());
    }

    @After
    public void testDown() throws Exception {
    }

    @Test
    public void testGenerateAuthenticateUrl() throws Exception {
        String returnUrl = "http://localhost:8080/client";
        String encodedUrl = URLEncoder.encode(returnUrl, "UTF-8");

        String authUrl = _oAuth2Auth.generateAuthenticationUrl(returnUrl);
        assertNotNull(authUrl);
        assertEquals(AUTHLOCATION + "?response_type=code&redirect_uri=" + encodedUrl + "&client_id=" + CLIENTID, authUrl);
    }

    @Configuration
    @ComponentScan(basePackageClasses = {OAuth2ManagerImpl.class}, includeFilters = {@Filter(value = TestConfiguration.Library.class, type = FilterType.CUSTOM)}, useDefaultFilters = false)
    public static class TestConfiguration extends SpringUtils.CloudStackTestConfiguration {

        @Bean
        public UserAccountDao userAccountDao() {
            return mock(UserAccountDao.class);
        }

        @Bean
        public DomainManager domainManager() {
            return mock(DomainManager.class);
        }

        @Bean
        public AccountManager accountManager() {
            return mock(AccountManager.class);
        }

        public static class Library implements TypeFilter {

            @Override
            public boolean match(MetadataReader mdr, MetadataReaderFactory arg1) throws IOException {
                ComponentScan cs = TestConfiguration.class.getAnnotation(ComponentScan.class);
                return SpringUtils.includedInBasePackageClasses(mdr.getClassMetadata().getClassName(), cs);
            }
        }
    }
}
