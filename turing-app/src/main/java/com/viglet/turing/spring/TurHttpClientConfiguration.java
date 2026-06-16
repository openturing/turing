package com.viglet.turing.spring;

import java.time.Duration;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.viglet.core.http.VigletHttpClientSettings;
import com.viglet.core.http.VigletHttpClients;

/**
 * T379 / Block Q — registers the shared outbound HTTP clients built by
 * viglet-core ({@link VigletHttpClients}): a JDK {@link java.net.http.HttpClient}
 * backed by a bounded executor and a pooled httpclient5
 * {@link CloseableHttpClient}. The pool/timeout values stay here as Turing's
 * config; the wiring lives in the shared module.
 */
@Configuration
public class TurHttpClientConfiguration {

    /** Bounded executor for the shared JDK client — stops unbounded thread creation. */
    private static final int SHARED_CLIENT_THREADS = 20;
    private static final Duration SHARED_CLIENT_CONNECT_TIMEOUT = Duration.ofSeconds(10);

    /** Pool sizing + timeouts for the httpclient5 proxy client. */
    private static final VigletHttpClientSettings PROXY_CLIENT_SETTINGS =
            new VigletHttpClientSettings(100, 20, Duration.ofSeconds(5), Duration.ofSeconds(30));

    @Bean
    public java.net.http.HttpClient sharedJavaHttpClient() {
        return VigletHttpClients.sharedJdkClient(SHARED_CLIENT_THREADS, SHARED_CLIENT_CONNECT_TIMEOUT);
    }

    @Bean(destroyMethod = "close")
    public CloseableHttpClient proxyHttpClient() {
        return VigletHttpClients.pooledClient(PROXY_CLIENT_SETTINGS);
    }
}
