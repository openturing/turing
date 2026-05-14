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

/**
 * One role-tagged message persisted in chat memory (MongoDB or Redis), as
 * exposed by the session-messages API. {@code timestamp} is the ISO-8601
 * string the store wrote when the turn happened.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public record TurChatSessionMessageDto(
        String role,
        String content,
        String timestamp) {
}
