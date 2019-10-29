package com.globo.globonetwork.cloudstack.manager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HealthCheckHelperTest {

    @Test
    public void testBuild() {
        HealthCheckHelper healthcheck = HealthCheckHelper.build("lb.test.com", "HTTP", "/health.html", "200 OK");
        assertEquals("HTTP", healthcheck.getHealthCheckType());
        assertEquals("200 OK", healthcheck.getExpectedHealthCheck());
        assertEquals("GET /health.html HTTP/1.0\\r\\nHost: lb.test.com\\r\\n\\r\\n", healthcheck.getHealthCheck());

        healthcheck = HealthCheckHelper.build("lb.test.com", "TCP", null, null);
        assertEquals("TCP", healthcheck.getHealthCheckType());
        assertEquals(null, healthcheck.getHealthCheck());
        assertEquals(null, healthcheck.getExpectedHealthCheck());
        assertNull(healthcheck.getExpectedHealthCheck());

    }

    @Test(expected=IllegalArgumentException.class)
    public void testBuild_validate() {
        HealthCheckHelper healthcheck = HealthCheckHelper.build("lb.test.com", "TCP", null, "WORKING");
        assertEquals("TCP", healthcheck.getHealthCheckType());
        assertEquals(null, healthcheck.getHealthCheck());
        assertEquals(null, healthcheck.getExpectedHealthCheck());
        assertNull(healthcheck.getExpectedHealthCheck());
    }

    @Test
    public void testBuild_http_old_version() {
        HealthCheckHelper healthcheck = HealthCheckHelper.build("lb.test.com", null, "/health.html", null);
        assertEquals("HTTP", healthcheck.getHealthCheckType());
        assertEquals("WORKING", healthcheck.getExpectedHealthCheck());
        assertEquals("GET /health.html HTTP/1.0\\r\\nHost: lb.test.com\\r\\n\\r\\n", healthcheck.getHealthCheck());
    }


    @Test
    public void testBuild_tcp_old_version() {
        HealthCheckHelper healthcheck = HealthCheckHelper.build("lb.test.com", null, null, null);
        assertEquals("TCP", healthcheck.getHealthCheckType());
        assertEquals(null, healthcheck.getHealthCheck());
        assertEquals(null, healthcheck.getExpectedHealthCheck());
        assertNull(healthcheck.getExpectedHealthCheck());
    }


    @Test
    public void testBuildHealthCheckExpectedStringEmpty(){
        HealthCheckHelper healthcheck = HealthCheckHelper.build("lb.test.com", "HTTP", "/healthcheck.html", "");
        assertEquals("WORKING", healthcheck.getExpectedHealthCheck());
        assertEquals("HTTP", healthcheck.getHealthCheckType());
        assertEquals("GET /healthcheck.html HTTP/1.0\\r\\nHost: lb.test.com\\r\\n\\r\\n", healthcheck.getHealthCheck());
    }

    @Test
    public void testBuildHealthCheckStringGivenPathAndHostNull(){
        HealthCheckHelper healthcheck = HealthCheckHelper.build(null, "HTTP", null, "200 OK");
        assertEquals("", healthcheck.buildHealthCheckString(null, null));
    }

    @Test
    public void testBuildHealthCheckStringGivenPathNullAndHostFilled(){
        HealthCheckHelper healthCheck = HealthCheckHelper.build("host", "HTTP", null, "200 OK");
        assertEquals("", healthCheck.buildHealthCheckString(null, "host"));

        assertEquals("200 OK", healthCheck.getExpectedHealthCheck());
        assertEquals("HTTP", healthCheck.getHealthCheckType());
    }

    @Test
    public void testBuildHealthCheckStringGiveFullHTTPPath(){
        HealthCheckHelper healthCheck = HealthCheckHelper.build("lb.test.com", "HTTP", "/healthcheck.html", "200 OK");
        assertEquals("GET /healthcheck.html", healthCheck.buildHealthCheckString("GET /healthcheck.html", "lb.test.com"));

        assertEquals("200 OK", healthCheck.getExpectedHealthCheck());
        assertEquals("HTTP", healthCheck.getHealthCheckType());
        assertEquals("GET /healthcheck.html HTTP/1.0\\r\\nHost: lb.test.com\\r\\n\\r\\n", healthCheck.getHealthCheck());
    }

    @Test
    public void testBuildHealthCheckStringGivenURIandHost(){
        HealthCheckHelper healthCheck = HealthCheckHelper.build("lb.test.com", "HTTP", "/healthcheck.html", "200 OK");
        assertEquals("GET /healtcheck.html HTTP/1.0\\r\\nHost: lb.test.com\\r\\n\\r\\n", healthCheck.buildHealthCheckString("/healtcheck.html", "lb.test.com"));
    }

}