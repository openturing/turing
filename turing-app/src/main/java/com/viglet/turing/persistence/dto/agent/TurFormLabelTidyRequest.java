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

import java.util.List;

/**
 * T236 / §VII.13.g — request body for the form-label "tidy" pass. Carries the
 * fields the T234 client-side converter produced; the service asks the default
 * LLM to rewrite each raw {@code label} into a short, clean form label while
 * leaving {@code name} / {@code type} untouched.
 *
 * @param fields the form fields whose labels should be tidied (in order).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurFormLabelTidyRequest(List<TurFormLabelTidyField> fields) {
}
