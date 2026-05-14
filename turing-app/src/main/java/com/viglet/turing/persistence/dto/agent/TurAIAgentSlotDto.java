/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.agent;

import com.viglet.turing.persistence.model.agent.TurAIAgentSlot;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Wire representation of {@link TurAIAgentSlot}. Extending the entity keeps
 * field shape and Jackson behaviour in lock-step with the persistence model
 * — same convention used by {@link TurChatFlowDto}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TurAIAgentSlotDto extends TurAIAgentSlot {
}
