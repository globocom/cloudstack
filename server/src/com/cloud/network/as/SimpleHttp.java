package com.cloud.network.as;

import com.cloud.utils.exception.CloudRuntimeException;


import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;

/**
 * Created by lucas.castro on 5/17/17.
 */
public class SimpleHttp {

    private static final Logger s_logger = Logger.getLogger(SimpleHttp.class.getName());

    private volatile HttpRequestFactory requestFactory;
    private static final Charset DEFAULT_CHARSET = Charset.forName("UTF-8");

    static final HttpTransport HTTP_TRANSPORT = new NetHttpTransport();
    static final JsonFactory JSON_FACTORY = new JacksonFactory();

    private int readTimeout = 200;
    private int connectTimeout = 200;
    private int numberOfRetries = 0;

    public SimpleHttp() {
    }

    public SimpleHttp(int readTimeout, int connectTimeout, int numberOfRetries) {
        this.readTimeout = readTimeout;
        this.connectTimeout = connectTimeout;
        this.numberOfRetries = numberOfRetries;
    }

    public String post(String url, String payload) {
        try{
            HttpContent body = null;

            if (payload != null) {
                final byte[] contentBytes = payload.getBytes();
                body = new ByteArrayContent("application/json", contentBytes);
            }

            HttpRequestFactory requestFactory = this.getRequestFactory();

            GenericUrl genericUrl = getUrl(url);
            HttpRequest request = requestFactory.buildRequest("POST", genericUrl, body);

            request.setLoggingEnabled(true);

            s_logger.debug("[SimpleHttp] requesting url:" + request.getUrl().toString() + ", body: " + body);
            HttpResponse response = request.execute();

            String responseContent = response.parseAsString();
            Integer statusCode = response.getStatusCode();

            s_logger.debug("[SimpleHttp] statusCode:" + statusCode + ", result: " + responseContent);

            return responseContent;
        } catch (HttpResponseException httpException){
            s_logger.error("[SimpleHttp] error while requesting. StatusCode: " + httpException.getStatusCode() + ", Content: " + httpException.getContent() + ", msg:" + httpException.getMessage(), httpException);

            throw new CloudRuntimeException("Error while requesting.", httpException);
        } catch (IOException e) {
            s_logger.error("[SimpleHttp] IOError " , e);
            throw new CloudRuntimeException("IOError while requesting.", e);
        }


    }
    protected HttpRequestFactory getRequestFactory() {
        if (this.requestFactory == null) {
            synchronized (this) {
                loadConfig();
                this.requestFactory = HTTP_TRANSPORT.createRequestFactory(new HttpRequestInitializer() {
                    public void initialize(HttpRequest request) throws IOException {
                        request.setParser(new JsonObjectParser(JSON_FACTORY));
                        request.setReadTimeout(SimpleHttp.this.readTimeout);
                        request.setConnectTimeout(SimpleHttp.this.connectTimeout);
                        request.setNumberOfRetries(SimpleHttp.this.numberOfRetries);
                    }
                });
            }
        }
        return this.requestFactory;
    }

    public static <T> T parse(String output, Class<T> dataType) throws CloudRuntimeException {
        try {
            InputStream stream = new ByteArrayInputStream(output.getBytes(DEFAULT_CHARSET));

            com.google.api.client.json.JsonFactory jsonFactory = new JacksonFactory();
            return new JsonObjectParser(jsonFactory).parseAndClose(stream, DEFAULT_CHARSET, dataType);

        } catch (IOException e) {
            throw new CloudRuntimeException("IOError while trying to parse : " + output + " to " + dataType + " " + e.getMessage() , e);
        }
    }

    private GenericUrl getUrl(String url) {
        try {
            return new GenericUrl(url);
        } catch (Exception e) {
            throw new CloudRuntimeException("Error building url. ", e);
        }
    }

    private static void loadConfig() {

        ConsoleHandler logHandler = new ConsoleHandler();
        logHandler.setLevel(Level.ALL);
        java.util.logging.Logger httpLogger = java.util.logging.Logger.getLogger("com.google.api.client.http");
        httpLogger.setLevel(Level.ALL);
        httpLogger.addHandler(logHandler);
    }
}
