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
package com.viglet.turing.testsupport;

import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

/**
 * T1108 — says out loud what {@code @Testcontainers(disabledWithoutDocker = true)}
 * says nowhere: that Docker could not be reached, so every container IT in this
 * run (the Solr, Elasticsearch and engine conformance ITs among them) was skipped
 * rather than run.
 *
 * <p>Without Docker this test is aborted with the reason, which the report lists
 * beside the skipped ITs. With {@code -Dturing.it.requireDocker=true} (a CI job
 * that is there to run them) it fails instead, so the job cannot go green on
 * containers it never started.
 *
 * <p>The usual cause on a recent engine is the API version: Docker 29 refuses the
 * 1.32 that docker-java opens with, which {@code docker-java.properties} on the test
 * classpath pins to 1.44.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
class TurDockerAvailabilityIT {

    @Test
    void dockerIsReachableForTheContainerITs() {
        if (DockerClientFactory.instance().isDockerAvailable()) {
            return;
        }
        String reason = "Docker is not reachable: every @Testcontainers(disabledWithoutDocker = true) IT in this "
                + "run is skipped, not run. Check the engine, and that it accepts the API version in "
                + "src/test/resources/docker-java.properties.";
        System.err.println("WARN " + reason);
        if (Boolean.getBoolean("turing.it.requireDocker")) {
            fail(reason);
        }
        Assumptions.abort(reason);
    }
}
