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

import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.model.auth.TurUser;
import com.viglet.turing.persistence.repository.auth.TurGroupRepository;
import com.viglet.turing.persistence.repository.auth.TurRoleRepository;
import com.viglet.turing.persistence.repository.auth.TurUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test for TurCustomUserDetailsService
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurCustomUserDetailsServiceTest {

    @Mock
    private TurUserRepository turUserRepository;

    @Mock
    private TurRoleRepository turRoleRepository;

    @Mock
    private TurGroupRepository turGroupRepository;

    @InjectMocks
    private TurCustomUserDetailsService turCustomUserDetailsService;

    @Test
    void testLoadUserByUsername_Success() {
        // Arrange
        String username = "testuser";
        TurUser turUser = new TurUser();
        turUser.setUsername(username);
        turUser.setPassword("password");

        TurGroup turGroup = new TurGroup();
        turGroup.setName("testgroup");

        TurRole turRole = new TurRole();
        turRole.setName("ROLE_USER");

        Set<TurGroup> groups = new HashSet<>();
        groups.add(turGroup);

        Set<TurRole> roles = new HashSet<>();
        roles.add(turRole);

        when(turUserRepository.findByUsername(username)).thenReturn(turUser);
        when(turGroupRepository.findByTurUsersContaining(turUser)).thenReturn(groups);
        when(turRoleRepository.findByTurGroupsContaining(turGroup)).thenReturn(roles);

        // Act
        UserDetails userDetails = turCustomUserDetailsService.loadUserByUsername(username);

        // Assert
        assertNotNull(userDetails);
        assertEquals(username, userDetails.getUsername());
        assertNotNull(userDetails.getAuthorities());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_USER")));
        
        verify(turUserRepository).findByUsername(username);
        verify(turGroupRepository).findByTurUsersContaining(turUser);
        verify(turRoleRepository).findByTurGroupsContaining(turGroup);
    }

    @Test
    void testLoadUserByUsername_UserNotFound() {
        // Arrange
        String username = "nonexistent";
        when(turUserRepository.findByUsername(username)).thenReturn(null);

        // Act & Assert
        UsernameNotFoundException exception = assertThrows(
                UsernameNotFoundException.class,
                () -> turCustomUserDetailsService.loadUserByUsername(username)
        );

        assertTrue(exception.getMessage().contains(username));
        verify(turUserRepository).findByUsername(username);
        verifyNoInteractions(turGroupRepository);
        verifyNoInteractions(turRoleRepository);
    }

    @Test
    void testLoadUserByUsername_WithMultipleRoles() {
        // Arrange
        String username = "adminuser";
        TurUser turUser = new TurUser();
        turUser.setUsername(username);

        TurGroup adminGroup = new TurGroup();
        adminGroup.setName("admins");

        TurGroup userGroup = new TurGroup();
        userGroup.setName("users");

        TurRole adminRole = new TurRole();
        adminRole.setName("ROLE_ADMIN");

        TurRole userRole = new TurRole();
        userRole.setName("ROLE_USER");

        Set<TurGroup> groups = new HashSet<>();
        groups.add(adminGroup);
        groups.add(userGroup);

        Set<TurRole> adminRoles = new HashSet<>();
        adminRoles.add(adminRole);

        Set<TurRole> userRoles = new HashSet<>();
        userRoles.add(userRole);

        when(turUserRepository.findByUsername(username)).thenReturn(turUser);
        when(turGroupRepository.findByTurUsersContaining(turUser)).thenReturn(groups);
        when(turRoleRepository.findByTurGroupsContaining(adminGroup)).thenReturn(adminRoles);
        when(turRoleRepository.findByTurGroupsContaining(userGroup)).thenReturn(userRoles);

        // Act
        UserDetails userDetails = turCustomUserDetailsService.loadUserByUsername(username);

        // Assert
        assertNotNull(userDetails);
        assertEquals(2, userDetails.getAuthorities().size());
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN")));
        assertTrue(userDetails.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void testLoadUserByUsername_WithNoRoles() {
        // Arrange
        String username = "noroleuser";
        TurUser turUser = new TurUser();
        turUser.setUsername(username);

        Set<TurGroup> emptyGroups = new HashSet<>();

        when(turUserRepository.findByUsername(username)).thenReturn(turUser);
        when(turGroupRepository.findByTurUsersContaining(turUser)).thenReturn(emptyGroups);

        // Act
        UserDetails userDetails = turCustomUserDetailsService.loadUserByUsername(username);

        // Assert
        assertNotNull(userDetails);
        assertTrue(userDetails.getAuthorities().isEmpty());
    }
}
