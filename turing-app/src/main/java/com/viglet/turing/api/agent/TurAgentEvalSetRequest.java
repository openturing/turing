/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.agent;

import java.util.List;

/**
 * Request body for creating/updating a golden {@code TurAgentEvalSet} together
 * with its inline cases. Mirrors the client-editable fields of the aggregate so
 * the persistent entity (and its cascade-persisted {@code TurAgentEvalCase}
 * children) is never bound directly from the HTTP request. The owning agent is
 * taken from the path, and case identities/order are assigned on save. JSON
 * field names match the entity's, so the editor contract is unchanged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
public record TurAgentEvalSetRequest(
        String name,
        String description,
        int enabled,
        int blocking,
        List<TurAgentEvalCaseRequest> cases) {
}
