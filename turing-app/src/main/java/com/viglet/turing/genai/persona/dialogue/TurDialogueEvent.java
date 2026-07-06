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

/**
 * One server-sent event in a streaming persona↔persona dialogue — Block AI /
 * §XXXII.8 (T585, streamed in T604). The dialogue is emitted turn by turn so the
 * client renders the conversation live instead of waiting for the whole
 * transcript.
 *
 * <ul>
 *   <li>{@code TURN} — a completed utterance ({@code index}, {@code personaId},
 *       {@code personaName}, {@code content});</li>
 *   <li>{@code DONE} — the dialogue finished normally ({@code index} = number of
 *       turns produced);</li>
 *   <li>{@code ERROR} — a turn failed mid-dialogue ({@code error} set); prior
 *       {@code TURN} events still stand, so the client keeps the partial thread.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurDialogueEvent(
        TurDialogueEventType type,
        Integer index,
        String personaId,
        String personaName,
        String content,
        String error) {

    public enum TurDialogueEventType {
        TURN, DONE, ERROR
    }

    public static TurDialogueEvent turn(int index, String personaId, String personaName, String content) {
        return new TurDialogueEvent(TurDialogueEventType.TURN, index, personaId, personaName, content, null);
    }

    public static TurDialogueEvent done(int turnsProduced) {
        return new TurDialogueEvent(TurDialogueEventType.DONE, turnsProduced, null, null, null, null);
    }

    public static TurDialogueEvent error(int index, String message) {
        return new TurDialogueEvent(TurDialogueEventType.ERROR, index, null, null, null, message);
    }
}
