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
 * One utterance in a persona↔persona dialogue — Block AI / §XXXII.8 (T585). The
 * speaker persona's id + name are carried alongside the generated text so the
 * client can render a two-sided transcript without a second lookup.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurDialogueTurn(String personaId, String personaName, String content) {
}
