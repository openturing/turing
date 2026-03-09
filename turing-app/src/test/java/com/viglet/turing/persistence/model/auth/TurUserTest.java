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

package com.viglet.turing.persistence.model.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TurUser.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurUserTest {

    @Test
    void testNoArgsConstructor() {
        TurUser turUser = new TurUser();
        
        assertThat(turUser).isNotNull();
        assertThat(turUser.getUsername()).isNull();
        assertThat(turUser.getEmail()).isNull();
        assertThat(turUser.getFirstName()).isNull();
        assertThat(turUser.getLastName()).isNull();
        assertThat(turUser.getPassword()).isNull();
        assertThat(turUser.getRealm()).isNull();
        assertThat(turUser.getEnabled()).isZero();
        assertThat(turUser.getLastLogin()).isNull();
        assertThat(turUser.getTurGroups()).isEmpty();
    }

    @Test
    void testBuilderPattern() {
        Instant now = Instant.now();
        TurUser turUser = TurUser.builder()
                .username("testuser")
                .email("test@example.com")
                .firstName("John")
                .lastName("Doe")
                .password("password123")
                .realm("default")
                .enabled(1)
                .lastLogin(now)
                .build();
        
        assertThat(turUser.getUsername()).isEqualTo("testuser");
        assertThat(turUser.getEmail()).isEqualTo("test@example.com");
        assertThat(turUser.getFirstName()).isEqualTo("John");
        assertThat(turUser.getLastName()).isEqualTo("Doe");
        assertThat(turUser.getPassword()).isEqualTo("password123");
        assertThat(turUser.getRealm()).isEqualTo("default");
        assertThat(turUser.getEnabled()).isEqualTo(1);
        assertThat(turUser.getLastLogin()).isEqualTo(now);
        assertThat(turUser.getTurGroups()).isEmpty();
    }

    @Test
    void testAllArgsConstructor() {
        Instant now = Instant.now();
        Collection<TurGroup> groups = new HashSet<>();
        
        TurUser turUser = new TurUser("admin", "admin@example.com", "Admin",
                now, "User", "pass", "realm", 1, null, groups);

        assertThat(turUser.getUsername()).isEqualTo("admin");
        assertThat(turUser.getEmail()).isEqualTo("admin@example.com");
        assertThat(turUser.getFirstName()).isEqualTo("Admin");
        assertThat(turUser.getLastName()).isEqualTo("User");
        assertThat(turUser.getPassword()).isEqualTo("pass");
        assertThat(turUser.getRealm()).isEqualTo("realm");
        assertThat(turUser.getEnabled()).isEqualTo(1);
        assertThat(turUser.getLastLogin()).isEqualTo(now);
        assertThat(turUser.getAvatarUrl()).isNull();
        assertThat(turUser.getTurGroups()).isEqualTo(groups);
    }

    @Test
    void testCopyConstructor() {
        TurUser original = TurUser.builder()
                .username("testuser")
                .email("test@example.com")
                .password("password123")
                .enabled(1)
                .build();
        
        TurUser copy = new TurUser(original);
        
        assertThat(copy.getUsername()).isEqualTo(original.getUsername());
        assertThat(copy.getEmail()).isEqualTo(original.getEmail());
        assertThat(copy.getPassword()).isEqualTo(original.getPassword());
        assertThat(copy.getEnabled()).isEqualTo(original.getEnabled());
    }

    @Test
    void testGettersAndSetters() {
        TurUser turUser = new TurUser();
        Instant now = Instant.now();
        
        turUser.setUsername("user123");
        turUser.setEmail("user@test.com");
        turUser.setFirstName("Jane");
        turUser.setLastName("Smith");
        turUser.setPassword("secret");
        turUser.setRealm("testing");
        turUser.setEnabled(1);
        turUser.setLastLogin(now);
        
        assertThat(turUser.getUsername()).isEqualTo("user123");
        assertThat(turUser.getEmail()).isEqualTo("user@test.com");
        assertThat(turUser.getFirstName()).isEqualTo("Jane");
        assertThat(turUser.getLastName()).isEqualTo("Smith");
        assertThat(turUser.getPassword()).isEqualTo("secret");
        assertThat(turUser.getRealm()).isEqualTo("testing");
        assertThat(turUser.getEnabled()).isEqualTo(1);
        assertThat(turUser.getLastLogin()).isEqualTo(now);
    }

    @Test
    void testSetTurGroupsWithValidCollection() {
        TurUser turUser = new TurUser();
        TurGroup group1 = new TurGroup();
        TurGroup group2 = new TurGroup();
        Collection<TurGroup> groups = Arrays.asList(group1, group2);
        
        turUser.setTurGroups(groups);
        
        assertThat(turUser.getTurGroups()).hasSize(2);
        assertThat(turUser.getTurGroups()).containsExactlyInAnyOrder(group1, group2);
    }

    @Test
    void testSetTurGroupsWithNull() {
        TurUser turUser = new TurUser();
        TurGroup group = new TurGroup();
        turUser.setTurGroups(Arrays.asList(group));
        
        assertThat(turUser.getTurGroups()).hasSize(1);
        
        turUser.setTurGroups(null);
        
        assertThat(turUser.getTurGroups()).isEmpty();
    }

    @Test
    void testSetTurGroupsReplacesExisting() {
        TurUser turUser = new TurUser();
        TurGroup group1 = new TurGroup();
        TurGroup group2 = new TurGroup();
        TurGroup group3 = new TurGroup();
        
        turUser.setTurGroups(Arrays.asList(group1, group2));
        assertThat(turUser.getTurGroups()).hasSize(2);
        
        turUser.setTurGroups(Arrays.asList(group3));
        assertThat(turUser.getTurGroups()).hasSize(1);
        assertThat(turUser.getTurGroups()).containsExactly(group3);
    }

    @Test
    void testToBuilder() {
        TurUser original = TurUser.builder()
                .username("original")
                .email("original@test.com")
                .enabled(1)
                .build();
        
        TurUser modified = original.toBuilder()
                .username("modified")
                .build();
        
        assertThat(modified.getUsername()).isEqualTo("modified");
        assertThat(modified.getEmail()).isEqualTo("original@test.com");
        assertThat(modified.getEnabled()).isEqualTo(1);
    }

    @Test
    void testSetEnabledWithDifferentValues() {
        TurUser turUser = new TurUser();
        
        turUser.setEnabled(0);
        assertThat(turUser.getEnabled()).isZero();
        
        turUser.setEnabled(1);
        assertThat(turUser.getEnabled()).isEqualTo(1);
    }

    @Test
    void testSerialVersionUID() {
        assertThat(TurUser.class)
                .hasDeclaredFields("serialVersionUID");
    }
}
