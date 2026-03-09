/*
 * Copyright (C) 2016-2022 the original author or authors.
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

package com.viglet.turing.spring.security;

import com.viglet.turing.persistence.model.auth.TurUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for TurCustomUserDetails
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
class TurCustomUserDetailsTest {

    @Test
    void testConstructor() {
        // Arrange
        TurUser turUser = new TurUser();
        turUser.setUsername("testuser");
        turUser.setPassword("password123");
        List<String> roles = Arrays.asList("ROLE_USER", "ROLE_ADMIN");

        // Act
        TurCustomUserDetails userDetails = new TurCustomUserDetails(turUser, roles);

        // Assert
        assertNotNull(userDetails);
        assertEquals("testuser", userDetails.getUsername());
        assertEquals("password123", userDetails.getPassword());
    }

    @Test
    void testGetAuthorities() {
        // Arrange
        TurUser turUser = new TurUser();
        turUser.setUsername("testuser");
        List<String> roles = Arrays.asList("ROLE_USER", "ROLE_ADMIN");

        // Act
        TurCustomUserDetails userDetails = new TurCustomUserDetails(turUser, roles);
        Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();

        // Assert
        assertNotNull(authorities);
        assertEquals(2, authorities.size());
        assertTrue(authorities.stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_USER")));
        assertTrue(authorities.stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void testGetAuthorities_EmptyRoles() {
        // Arrange
        TurUser turUser = new TurUser();
        turUser.setUsername("testuser");
        List<String> roles = List.of();

        // Act
        TurCustomUserDetails userDetails = new TurCustomUserDetails(turUser, roles);
        Collection<? extends GrantedAuthority> authorities = userDetails.getAuthorities();

        // Assert
        assertNotNull(authorities);
        assertTrue(authorities.isEmpty());
    }

    @Test
    void testIsAccountNonExpired() {
        // Arrange
        TurUser turUser = new TurUser();
        turUser.setUsername("testuser");
        TurCustomUserDetails userDetails = new TurCustomUserDetails(turUser, List.of());

        // Act & Assert
        assertTrue(userDetails.isAccountNonExpired());
    }

    @Test
    void testIsAccountNonLocked() {
        // Arrange
        TurUser turUser = new TurUser();
        turUser.setUsername("testuser");
        TurCustomUserDetails userDetails = new TurCustomUserDetails(turUser, List.of());

        // Act & Assert
        assertTrue(userDetails.isAccountNonLocked());
    }

    @Test
    void testIsCredentialsNonExpired() {
        // Arrange
        TurUser turUser = new TurUser();
        turUser.setUsername("testuser");
        TurCustomUserDetails userDetails = new TurCustomUserDetails(turUser, List.of());

        // Act & Assert
        assertTrue(userDetails.isCredentialsNonExpired());
    }

    @Test
    void testIsEnabled() {
        // Arrange
        TurUser turUser = new TurUser();
        turUser.setUsername("testuser");
        TurCustomUserDetails userDetails = new TurCustomUserDetails(turUser, List.of());

        // Act & Assert
        assertTrue(userDetails.isEnabled());
    }
}
