/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.persona.dialogue;

import java.util.List;

/**
 * The full transcript of an automatic persona↔persona dialogue — Block AI /
 * §XXXII.8 (T585). Carries the topic, the two speakers, the requested turn
 * budget, and the ordered list of utterances. {@code llmError} + {@code error}
 * surface a mid-dialogue failure (the transcript still holds every turn that
 * completed before it) so the client can render a partial conversation with a
 * clear notice rather than an opaque 500.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurDialogueTranscript(
        String topic,
        TurPersonaRef personaA,
        TurPersonaRef personaB,
        int turns,
        List<TurDialogueTurn> transcript,
        boolean llmError,
        String error) {

    /** A speaker reference (id + display name) for the transcript header. */
    public record TurPersonaRef(String id, String name) {
    }
}
