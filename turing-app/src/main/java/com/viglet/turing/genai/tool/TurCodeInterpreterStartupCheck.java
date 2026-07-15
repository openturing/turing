/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.genai.tool;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.viglet.turing.genai.tool.TurCodeInterpreterToolService.TurDockerStatus;
import com.viglet.turing.system.TurGlobalSettingsService;

import lombok.extern.slf4j.Slf4j;

/**
 * Startup diagnostic for the Code Interpreter DOCKER execution mode (T80).
 *
 * <p>When Turing itself runs inside a container, the {@code DOCKER} execution
 * mode needs a Docker CLI on {@code PATH} <em>and</em> a reachable daemon
 * (typically the host socket bind-mounted for Docker-out-of-Docker, or a DinD
 * sidecar). Getting that wiring wrong is silent otherwise: the mode is stored in
 * Global Settings, the app boots fine, and the misconfiguration only surfaces
 * the first time an agent calls {@code execute_python} — as a confusing tool
 * error mid-chat.
 *
 * <p>This listener probes availability once, right after the context is ready,
 * and emits a single loud, actionable log line so the operator sees the problem
 * at boot rather than in production traffic. It never throws: consistent with
 * the sandbox's degrade-don't-brick doctrine, a bad Docker setup must not stop
 * the whole application from starting — it just means the Code Interpreter is
 * unusable until fixed (and each execution will surface its own error).
 *
 * <p>Runs on every boot (not gated by {@code FIRST_TIME}) because the mode and
 * the surrounding infrastructure can change between restarts.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurCodeInterpreterStartupCheck {

    private final TurGlobalSettingsService turGlobalSettingsService;
    private final TurCodeInterpreterToolService turCodeInterpreterToolService;

    public TurCodeInterpreterStartupCheck(TurGlobalSettingsService turGlobalSettingsService,
            TurCodeInterpreterToolService turCodeInterpreterToolService) {
        this.turGlobalSettingsService = turGlobalSettingsService;
        this.turCodeInterpreterToolService = turCodeInterpreterToolService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verifyDockerModeReadiness() {
        TurCodeInterpreterExecutionMode mode = resolveMode();
        if (mode != TurCodeInterpreterExecutionMode.DOCKER) {
            log.debug("[CodeInterpreter] execution mode is {} — skipping Docker readiness probe", mode);
            return;
        }

        TurDockerStatus status = turCodeInterpreterToolService.checkDocker();
        if (status.available()) {
            log.info("[CodeInterpreter] DOCKER execution mode is READY — Docker daemon reachable "
                    + "(server {}). Sandbox image: {}",
                    status.serverVersion(), resolveImage());
            return;
        }

        // Loud, actionable diagnostic. The most common cause when Turing runs in
        // a container is a missing docker CLI in the image or an unmounted host
        // socket (Docker-out-of-Docker). Point the operator straight at both.
        log.error("""
                [CodeInterpreter] DOCKER execution mode is SELECTED but the Docker daemon is NOT reachable: {}
                  The Code Interpreter will FAIL on every execution until this is fixed.
                  When Turing runs inside a container, the DOCKER mode needs BOTH:
                    1) a `docker` CLI on PATH inside the Turing container
                       (the official images ship it; custom images must add it), and
                    2) access to a Docker daemon — either the host socket
                       (Docker-out-of-Docker): mount `/var/run/docker.sock:/var/run/docker.sock`
                       and add the Turing process to the socket's group (compose `group_add`),
                       or a Docker-in-Docker sidecar via DOCKER_HOST.
                  IMPORTANT (Docker-out-of-Docker): the per-execution session directory is
                  bind-mounted by ABSOLUTE PATH ({}), which the HOST daemon resolves against the
                  HOST filesystem. That path must therefore exist on the host at the SAME location
                  — bind-mount the host store dir to `/app/store` (not a named volume), see
                  docker-compose.yaml. Alternatively switch back to NATIVE mode in
                  Console -> Global Settings -> Code Interpreter.""",
                status.error(), storeSandboxPathHint());
    }

    private TurCodeInterpreterExecutionMode resolveMode() {
        try {
            TurCodeInterpreterExecutionMode mode =
                    turGlobalSettingsService.getCodeInterpreterExecutionMode();
            return mode == null ? TurCodeInterpreterExecutionMode.DEFAULT : mode;
        } catch (Exception e) {
            log.debug("[CodeInterpreter] could not resolve execution mode at startup ({}); "
                    + "assuming {}", e.getMessage(), TurCodeInterpreterExecutionMode.DEFAULT);
            return TurCodeInterpreterExecutionMode.DEFAULT;
        }
    }

    private String resolveImage() {
        try {
            return turGlobalSettingsService.getCodeInterpreterDockerImage();
        } catch (Exception e) {
            return "(unknown)";
        }
    }

    /** Best-effort hint of where session dirs land, for the error message. */
    private static String storeSandboxPathHint() {
        try {
            return new java.io.File(System.getProperty("user.dir", "."),
                    "store/code-interpreter").getAbsolutePath();
        } catch (Exception e) {
            return "<store>/code-interpreter";
        }
    }
}
