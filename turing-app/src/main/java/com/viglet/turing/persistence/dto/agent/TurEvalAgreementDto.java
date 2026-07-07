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
 * T594 / §XXXIII.9 — inter-annotator agreement over an agent's multi-reviewer
 * review tasks (Fleiss' kappa + observed agreement + Landis &amp; Koch band).
 *
 * @param items            number of multi-reviewer items that contributed
 * @param minRaters        smallest reviewer count across those items
 * @param maxRaters        largest reviewer count across those items
 * @param percentAgreement mean observed agreement in [0,1] (null when no data)
 * @param kappa            Fleiss' kappa (null when undefined / no data)
 * @param interpretation   the strength band ({@code "moderate"}, …, {@code "n/a"})
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public record TurEvalAgreementDto(
        int items,
        int minRaters,
        int maxRaters,
        Double percentAgreement,
        Double kappa,
        String interpretation) {
}
