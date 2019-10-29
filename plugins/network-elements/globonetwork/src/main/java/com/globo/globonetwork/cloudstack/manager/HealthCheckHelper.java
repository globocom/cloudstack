package com.globo.globonetwork.cloudstack.manager;

import java.io.Serializable;

/**
 * Created by lucas.castro on 3/10/16.
 */
public class HealthCheckHelper implements Serializable{

        private String host;
        private String healthCheckType;
        private String healthCheck;
        private String expectedHealthCheck;

        private static final String DEFAULT_EXPECT_FOR_HTTP_HEALTHCHECK = "WORKING";

        private HealthCheckHelper(String host) {
            this.host = host;
        }

        public String getHealthCheckType() {
            return healthCheckType;
        }

        public String getHealthCheck() {
            return healthCheck;
        }

        public String getExpectedHealthCheck() {
            return expectedHealthCheck;
        }

        protected HealthCheckHelper build(String healthcheckType, String healthcheck, String expectedHealthcheck) {
            this.healthCheck = this.buildHealthCheckString(healthcheck, host);

            //old version, client just pass healthcheckpath
            if (healthcheck != null && !healthcheck.isEmpty() &&
                    ((expectedHealthcheck == null || expectedHealthcheck.isEmpty()) ||
                    (healthcheckType == null || healthcheckType.isEmpty()))
                    )
            {
                this.healthCheckType = HealthCheckType.HTTP.name();
                this.expectedHealthCheck = DEFAULT_EXPECT_FOR_HTTP_HEALTHCHECK;
                return this;
            }

            //new version
            if ( healthcheckType != null ){
                this.healthCheckType = HealthCheckType.valueOf(healthcheckType).name();

                if  (HealthCheckType.isLayer4(healthCheckType)) {
                    this.expectedHealthCheck = null;
                    this.healthCheck = null;
                } else {
                    this.expectedHealthCheck = expectedHealthcheck != null ? expectedHealthcheck : DEFAULT_EXPECT_FOR_HTTP_HEALTHCHECK;
                }
            } else {
                this.healthCheckType = HealthCheckType.TCP.name();
                this.expectedHealthCheck = null;
                this.healthCheck = null;
            }

            return this;
        }

        protected String buildHealthCheckString(String path, String host) {
            if (path == null || path.equals("") || host == null) {
                return "";
            }
            if (path.startsWith("GET") || path.startsWith("POST")) {
                return path;
            }
            return "GET " + path + " HTTP/1.0\\r\\nHost: " + host + "\\r\\n\\r\\n";
        }


        public static void validate(String host, String healthehckType, String healthcheck, String expectedHealthcheck){


            if ( healthehckType != null && HealthCheckType.isLayer4(healthehckType)){

                if (expectedHealthcheck != null && !expectedHealthcheck.isEmpty()) {
                    throw new IllegalArgumentException("Health check validation error: when health check type is TCP/UDP 'Expected health check' should be empty! type: " + healthehckType + ", 'expected health check': " + expectedHealthcheck);
                }

                if (healthcheck != null && !healthcheck.isEmpty()) {
                    throw new IllegalArgumentException("Health check validation error: When health check type is TCP/UDP 'HealthCheck request' should be empty! type: " + healthehckType + ", 'health check request': " + healthcheck);
                }
            }
        }

        public static HealthCheckHelper build(String host, String healthehckType, String healthCheck, String expectedHealthCheck) {
            HealthCheckHelper healthCheckHelper = new HealthCheckHelper(host);
            validate(host, healthehckType, healthCheck, expectedHealthCheck);
            return healthCheckHelper.build(healthehckType, healthCheck, expectedHealthCheck);
        }

    public static String getL4Protocol(String healthcheckType, Integer vipPort) {
        if(HealthCheckType.isLayer7(healthcheckType)){
            return HealthCheckType.TCP.name();
        } else{
            return healthcheckType.toUpperCase();
        }
    }

    public static String getL7Protocol(String healthcheckType, Integer vipPort) {
        if(!HealthCheckType.isLayer7(healthcheckType)){
            return "Outros";
        } else if (vipPort == 443 || vipPort == 8443) {
            return HealthCheckType.HTTPS.name();
        } else if (vipPort == 80 || vipPort == 8080) {
            return HealthCheckType.HTTP.name();
        } else{
            return healthcheckType.toUpperCase();
        }
    }

    public enum HealthCheckType {
        HTTP(7), HTTPS(7), TCP(4), UDP(4);

        private int layer;

        private HealthCheckType(int layer){
            this.layer = layer;
        }

        public static boolean isLayer4(String healthCheckType) {
            HealthCheckType healthType = HealthCheckType.valueOf(healthCheckType);
            return healthType.layer == 4;
        }
        public static boolean isLayer7(String healthCheckType) {
            HealthCheckType healthType = HealthCheckType.valueOf(healthCheckType);
            return healthType.layer == 7;
        }
    }
}

