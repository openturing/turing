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

package com.viglet.turing.persistence.bean.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TurDataGroupSentenceBean.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurDataGroupSentenceBeanTest {

    private TurDataGroupSentenceBean turDataGroupSentenceBean;

    @BeforeEach
    void setUp() {
        turDataGroupSentenceBean = new TurDataGroupSentenceBean();
    }

    @Test
    void testGettersAndSetters() {
        int id = 123;
        String sentence = "This is a test sentence";
        int turData = 456;
        int turDataGroup = 789;
        int turMLCategory = 101;

        turDataGroupSentenceBean.setId(id);
        turDataGroupSentenceBean.setSentence(sentence);
        turDataGroupSentenceBean.setTurData(turData);
        turDataGroupSentenceBean.setTurDataGroup(turDataGroup);
        turDataGroupSentenceBean.setTurMLCategory(turMLCategory);

        assertThat(turDataGroupSentenceBean.getId()).isEqualTo(id);
        assertThat(turDataGroupSentenceBean.getSentence()).isEqualTo(sentence);
        assertThat(turDataGroupSentenceBean.getTurData()).isEqualTo(turData);
        assertThat(turDataGroupSentenceBean.getTurDataGroup()).isEqualTo(turDataGroup);
        assertThat(turDataGroupSentenceBean.getTurMLCategory()).isEqualTo(turMLCategory);
    }

    @Test
    void testDefaultConstructor() {
        assertThat(turDataGroupSentenceBean).isNotNull();
        assertThat(turDataGroupSentenceBean.getId()).isZero();
        assertThat(turDataGroupSentenceBean.getSentence()).isNull();
        assertThat(turDataGroupSentenceBean.getTurData()).isZero();
        assertThat(turDataGroupSentenceBean.getTurDataGroup()).isZero();
        assertThat(turDataGroupSentenceBean.getTurMLCategory()).isZero();
    }

    @Test
    void testIdField() {
        turDataGroupSentenceBean.setId(1);
        assertThat(turDataGroupSentenceBean.getId()).isEqualTo(1);

        turDataGroupSentenceBean.setId(999);
        assertThat(turDataGroupSentenceBean.getId()).isEqualTo(999);
    }

    @Test
    void testSentenceField() {
        String sentence1 = "First sentence";
        turDataGroupSentenceBean.setSentence(sentence1);
        assertThat(turDataGroupSentenceBean.getSentence()).isEqualTo(sentence1);

        String sentence2 = "Second sentence";
        turDataGroupSentenceBean.setSentence(sentence2);
        assertThat(turDataGroupSentenceBean.getSentence()).isEqualTo(sentence2);
    }

    @Test
    void testTurDataField() {
        turDataGroupSentenceBean.setTurData(100);
        assertThat(turDataGroupSentenceBean.getTurData()).isEqualTo(100);

        turDataGroupSentenceBean.setTurData(200);
        assertThat(turDataGroupSentenceBean.getTurData()).isEqualTo(200);
    }

    @Test
    void testTurDataGroupField() {
        turDataGroupSentenceBean.setTurDataGroup(300);
        assertThat(turDataGroupSentenceBean.getTurDataGroup()).isEqualTo(300);

        turDataGroupSentenceBean.setTurDataGroup(400);
        assertThat(turDataGroupSentenceBean.getTurDataGroup()).isEqualTo(400);
    }

    @Test
    void testTurMLCategoryField() {
        turDataGroupSentenceBean.setTurMLCategory(10);
        assertThat(turDataGroupSentenceBean.getTurMLCategory()).isEqualTo(10);

        turDataGroupSentenceBean.setTurMLCategory(20);
        assertThat(turDataGroupSentenceBean.getTurMLCategory()).isEqualTo(20);
    }

    @Test
    void testAllFieldsTogether() {
        turDataGroupSentenceBean.setId(42);
        turDataGroupSentenceBean.setSentence("Complete test sentence");
        turDataGroupSentenceBean.setTurData(84);
        turDataGroupSentenceBean.setTurDataGroup(168);
        turDataGroupSentenceBean.setTurMLCategory(336);

        assertThat(turDataGroupSentenceBean.getId()).isEqualTo(42);
        assertThat(turDataGroupSentenceBean.getSentence()).isEqualTo("Complete test sentence");
        assertThat(turDataGroupSentenceBean.getTurData()).isEqualTo(84);
        assertThat(turDataGroupSentenceBean.getTurDataGroup()).isEqualTo(168);
        assertThat(turDataGroupSentenceBean.getTurMLCategory()).isEqualTo(336);
    }

    @Test
    void testNullSentence() {
        turDataGroupSentenceBean.setSentence(null);
        assertThat(turDataGroupSentenceBean.getSentence()).isNull();
    }

    @Test
    void testEmptySentence() {
        turDataGroupSentenceBean.setSentence("");
        assertThat(turDataGroupSentenceBean.getSentence()).isEmpty();
    }

    @Test
    void testLongSentence() {
        String longSentence = "This is a very long sentence that contains many words and should still be handled correctly by the bean.";
        turDataGroupSentenceBean.setSentence(longSentence);
        assertThat(turDataGroupSentenceBean.getSentence()).isEqualTo(longSentence);
    }

    @Test
    void testSentenceWithSpecialCharacters() {
        String specialSentence = "Sentence with special chars: @#$%^&*()";
        turDataGroupSentenceBean.setSentence(specialSentence);
        assertThat(turDataGroupSentenceBean.getSentence()).isEqualTo(specialSentence);
    }

    @Test
    void testSentenceWithUnicode() {
        String unicodeSentence = "Sentence with unicode: 你好 мир";
        turDataGroupSentenceBean.setSentence(unicodeSentence);
        assertThat(turDataGroupSentenceBean.getSentence()).isEqualTo(unicodeSentence);
    }

    @Test
    void testZeroValues() {
        turDataGroupSentenceBean.setId(0);
        turDataGroupSentenceBean.setTurData(0);
        turDataGroupSentenceBean.setTurDataGroup(0);
        turDataGroupSentenceBean.setTurMLCategory(0);

        assertThat(turDataGroupSentenceBean.getId()).isZero();
        assertThat(turDataGroupSentenceBean.getTurData()).isZero();
        assertThat(turDataGroupSentenceBean.getTurDataGroup()).isZero();
        assertThat(turDataGroupSentenceBean.getTurMLCategory()).isZero();
    }

    @Test
    void testNegativeValues() {
        turDataGroupSentenceBean.setId(-1);
        turDataGroupSentenceBean.setTurData(-2);
        turDataGroupSentenceBean.setTurDataGroup(-3);
        turDataGroupSentenceBean.setTurMLCategory(-4);

        assertThat(turDataGroupSentenceBean.getId()).isEqualTo(-1);
        assertThat(turDataGroupSentenceBean.getTurData()).isEqualTo(-2);
        assertThat(turDataGroupSentenceBean.getTurDataGroup()).isEqualTo(-3);
        assertThat(turDataGroupSentenceBean.getTurMLCategory()).isEqualTo(-4);
    }

    @Test
    void testLargeIntegerValues() {
        turDataGroupSentenceBean.setId(Integer.MAX_VALUE);
        turDataGroupSentenceBean.setTurData(Integer.MAX_VALUE - 1);
        turDataGroupSentenceBean.setTurDataGroup(Integer.MAX_VALUE - 2);
        turDataGroupSentenceBean.setTurMLCategory(Integer.MAX_VALUE - 3);

        assertThat(turDataGroupSentenceBean.getId()).isEqualTo(Integer.MAX_VALUE);
        assertThat(turDataGroupSentenceBean.getTurData()).isEqualTo(Integer.MAX_VALUE - 1);
        assertThat(turDataGroupSentenceBean.getTurDataGroup()).isEqualTo(Integer.MAX_VALUE - 2);
        assertThat(turDataGroupSentenceBean.getTurMLCategory()).isEqualTo(Integer.MAX_VALUE - 3);
    }

    @Test
    void testMultipleSets() {
        turDataGroupSentenceBean.setId(1);
        turDataGroupSentenceBean.setId(2);
        turDataGroupSentenceBean.setId(3);
        assertThat(turDataGroupSentenceBean.getId()).isEqualTo(3);

        turDataGroupSentenceBean.setSentence("First");
        turDataGroupSentenceBean.setSentence("Second");
        turDataGroupSentenceBean.setSentence("Third");
        assertThat(turDataGroupSentenceBean.getSentence()).isEqualTo("Third");
    }
}
