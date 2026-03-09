/*
 * Copyright (C) 2016-2025 the original author or authors.
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

package com.viglet.turing.persistence.model.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for TurLocale.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurLocaleTest {

    @Test
    void testNoArgsConstructor() {
        TurLocale turLocale = new TurLocale();

        assertThat(turLocale).isNotNull();
        assertThat(turLocale.getInitials()).isNull();
        assertThat(turLocale.getEn()).isNull();
        assertThat(turLocale.getPt()).isNull();
    }

    @Test
    void testAllArgsConstructor() {
        Locale locale = Locale.US;
        TurLocale turLocale = new TurLocale(locale, "English", "Inglês");

        assertThat(turLocale.getInitials()).isEqualTo(locale);
        assertThat(turLocale.getEn()).isEqualTo("English");
        assertThat(turLocale.getPt()).isEqualTo("Inglês");
    }

    @Test
    void testGettersAndSetters() {
        TurLocale turLocale = new TurLocale();
        Locale locale = Locale.FRANCE;

        turLocale.setInitials(locale);
        turLocale.setEn("French");
        turLocale.setPt("Francês");

        assertThat(turLocale.getInitials()).isEqualTo(locale);
        assertThat(turLocale.getEn()).isEqualTo("French");
        assertThat(turLocale.getPt()).isEqualTo("Francês");
    }

    @Test
    void testSettersWithNullValues() {
        TurLocale turLocale = new TurLocale(Locale.US, "English", "Inglês");

        turLocale.setInitials(null);
        turLocale.setEn(null);
        turLocale.setPt(null);

        assertThat(turLocale.getInitials()).isNull();
        assertThat(turLocale.getEn()).isNull();
        assertThat(turLocale.getPt()).isNull();
    }

    @Test
    void testMultipleLocales() {
        TurLocale enLocale = new TurLocale(Locale.US, "English", "Inglês");
        TurLocale ptLocale = new TurLocale(Locale.of("pt", "BR"), "Portuguese", "Português");
        TurLocale esLocale = new TurLocale(Locale.of("es"), "Spanish", "Espanhol");

        assertThat(enLocale.getInitials()).isEqualTo(Locale.US);
        assertThat(ptLocale.getInitials()).isEqualTo(Locale.of("pt", "BR"));
        assertThat(esLocale.getInitials()).isEqualTo(Locale.of("es"));
    }

    @Test
    void testSerialVersionUID() {
        assertThat(TurLocale.class)
                .hasDeclaredFields("serialVersionUID");
    }
}
