/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.solr;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * T1110 / §CXXIII — {@link TurSolrUtils#createCore} and
 * {@link TurSolrUtils#deleteCore} against the Solr the Turing image ships (9.6.1)
 * and the latest Solr (10, user-managed), from the {@code en} configset that image
 * carries. The v2 core API takes a different body on each, so the call that
 * works on one is the call the other refuses.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
@Testcontainers(disabledWithoutDocker = true)
class TurSolrCoreAdminIT {

    private static final Path CONFIG_SET = Path.of("..", "containers", "solr", "config", "solr", "configsets",
            "en").toAbsolutePath().normalize();

    private static final String STAGED_CONFIG_SET = "/tmp/configsets/en";

    /**
     * Copies the staged configset to {@code $SOLR_HOME/configsets} as the {@code solr}
     * user, once Solr is up. Copied there before start, Testcontainers creates
     * {@code /var/solr/data} as root and Solr cannot write a new core's properties.
     */
    static void installConfigSet(GenericContainer<?> solr) throws Exception {
        var result = solr.execInContainer("bash", "-c",
                "mkdir -p /var/solr/data/configsets && cp -r " + STAGED_CONFIG_SET + " /var/solr/data/configsets/en");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
    }

    static Stream<Arguments> solrVersions() {
        return Stream.of(Arguments.of("solr:9.6.1", false), Arguments.of("solr:10.0.0", true));
    }

    private static int status(String url) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).statusCode();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("solrVersions")
    void createsAndDeletesACoreFromTheTuringConfigSet(String image, boolean userManaged) throws Exception {
        try (GenericContainer<?> solr = new GenericContainer<>(DockerImageName.parse(image))
                .withExposedPorts(8983)
                .withCopyToContainer(MountableFile.forHostPath(CONFIG_SET, 0777), STAGED_CONFIG_SET)
                .waitingFor(Wait.forHttp("/solr/admin/info/system").forStatusCode(200))) {
            if (userManaged) {
                // The solr:10 image starts in SolrCloud mode unless told otherwise.
                solr.withCommand("solr-foreground", "--user-managed");
            }
            solr.start();
            String host = "http://" + solr.getHost() + ":" + solr.getMappedPort(8983);

            installConfigSet(solr);

            assertThat(TurSolrUtils.createCore(host, "site_en", "en")).as("created").isTrue();
            assertThat(status(host + "/solr/site_en/admin/ping")).isEqualTo(200);

            TurSolrUtils.deleteCore(host, "site_en");
            assertThat(status(host + "/solr/site_en/admin/ping")).as("unloaded").isEqualTo(404);

            assertThat(TurSolrUtils.createCore(host, "broken", "no-such-configset"))
                    .as("a refused create is reported, not taken for success").isFalse();
        }
    }
}
