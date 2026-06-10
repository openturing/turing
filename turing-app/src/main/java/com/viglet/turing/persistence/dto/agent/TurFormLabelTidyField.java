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
 * T236 / §VII.13.g — one form field carried into / out of the "tidy labels"
 * pass. On the request side {@code label} holds the raw label the T234
 * converter derived from a question's instruction
 * (e.g. {@code "Qual o seu e-mail corporativo?"}); on the response side it
 * holds the LLM-cleaned form label (e.g. {@code "E-mail"}).
 *
 * <p>{@code name} (the field's slot) and {@code type} (the widget) are echoed
 * back untouched — they exist only to give the model context and to let the
 * client re-pair the tidied label with the right field.
 *
 * @param name  the field's slot name — context only, never rewritten.
 * @param label the (raw → tidied) human-readable label.
 * @param type  the widget hint (text/email/tel/…) — context only.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurFormLabelTidyField(String name, String label, String type) {
}
