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

import java.util.Arrays;
import java.util.Collection;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for TurGroup.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurGroupTest {

    @Test
    void testDefaultConstructor() {
        TurGroup turGroup = new TurGroup();
        
        assertThat(turGroup).isNotNull();
        assertThat(turGroup.getId()).isNull();
        assertThat(turGroup.getName()).isNull();
        assertThat(turGroup.getDescription()).isNull();
        assertThat(turGroup.getTurRoles()).isEmpty();
        assertThat(turGroup.getTurUsers()).isEmpty();
    }

    @Test
    void testGettersAndSetters() {
        TurGroup turGroup = new TurGroup();
        
        turGroup.setId("group-id-456");
        turGroup.setName("Administrators");
        turGroup.setDescription("Admin group");
        
        assertThat(turGroup.getId()).isEqualTo("group-id-456");
        assertThat(turGroup.getName()).isEqualTo("Administrators");
        assertThat(turGroup.getDescription()).isEqualTo("Admin group");
    }

    @Test
    void testSetTurUsersWithValidCollection() {
        TurGroup turGroup = new TurGroup();
        TurUser user1 = new TurUser();
        TurUser user2 = new TurUser();
        Collection<TurUser> users = Arrays.asList(user1, user2);
        
        turGroup.setTurUsers(users);
        
        assertThat(turGroup.getTurUsers()).hasSize(2);
        assertThat(turGroup.getTurUsers()).containsExactlyInAnyOrder(user1, user2);
    }

    @Test
    void testSetTurUsersWithNull() {
        TurGroup turGroup = new TurGroup();
        TurUser user = new TurUser();
        turGroup.setTurUsers(Arrays.asList(user));
        
        assertThat(turGroup.getTurUsers()).hasSize(1);
        
        turGroup.setTurUsers(null);
        
        assertThat(turGroup.getTurUsers()).isEmpty();
    }

    @Test
    void testSetTurUsersReplacesExisting() {
        TurGroup turGroup = new TurGroup();
        TurUser user1 = new TurUser();
        TurUser user2 = new TurUser();
        TurUser user3 = new TurUser();
        
        turGroup.setTurUsers(Arrays.asList(user1, user2));
        assertThat(turGroup.getTurUsers()).hasSize(2);
        
        turGroup.setTurUsers(Arrays.asList(user3));
        assertThat(turGroup.getTurUsers()).hasSize(1);
        assertThat(turGroup.getTurUsers()).containsExactly(user3);
    }

    @Test
    void testSetTurRolesWithValidCollection() {
        TurGroup turGroup = new TurGroup();
        TurRole role1 = new TurRole();
        TurRole role2 = new TurRole();
        Collection<TurRole> roles = Arrays.asList(role1, role2);
        
        turGroup.setTurRoles(roles);
        
        assertThat(turGroup.getTurRoles()).hasSize(2);
        assertThat(turGroup.getTurRoles()).containsExactlyInAnyOrder(role1, role2);
    }

    @Test
    void testSetTurRolesWithNull() {
        TurGroup turGroup = new TurGroup();
        TurRole role = new TurRole();
        turGroup.setTurRoles(Arrays.asList(role));
        
        assertThat(turGroup.getTurRoles()).hasSize(1);
        
        turGroup.setTurRoles(null);
        
        assertThat(turGroup.getTurRoles()).isEmpty();
    }

    @Test
    void testSetTurRolesReplacesExisting() {
        TurGroup turGroup = new TurGroup();
        TurRole role1 = new TurRole();
        TurRole role2 = new TurRole();
        TurRole role3 = new TurRole();
        
        turGroup.setTurRoles(Arrays.asList(role1, role2));
        assertThat(turGroup.getTurRoles()).hasSize(2);
        
        turGroup.setTurRoles(Arrays.asList(role3));
        assertThat(turGroup.getTurRoles()).hasSize(1);
        assertThat(turGroup.getTurRoles()).containsExactly(role3);
    }

    @Test
    void testSetNameWithDifferentValues() {
        TurGroup turGroup = new TurGroup();
        
        turGroup.setName("Admins");
        assertThat(turGroup.getName()).isEqualTo("Admins");
        
        turGroup.setName("Users");
        assertThat(turGroup.getName()).isEqualTo("Users");
        
        turGroup.setName("Guests");
        assertThat(turGroup.getName()).isEqualTo("Guests");
    }

    @Test
    void testSetDescriptionWithNullValue() {
        TurGroup turGroup = new TurGroup();
        turGroup.setDescription("Initial description");
        
        assertThat(turGroup.getDescription()).isEqualTo("Initial description");
        
        turGroup.setDescription(null);
        
        assertThat(turGroup.getDescription()).isNull();
    }

    @Test
    void testSetIdWithNullValue() {
        TurGroup turGroup = new TurGroup();
        turGroup.setId("test-id");
        
        assertThat(turGroup.getId()).isEqualTo("test-id");
        
        turGroup.setId(null);
        
        assertThat(turGroup.getId()).isNull();
    }

    @Test
    void testMultipleGroupsIndependence() {
        TurGroup adminGroup = new TurGroup();
        TurGroup userGroup = new TurGroup();
        
        adminGroup.setName("Admins");
        adminGroup.setDescription("Administrator group");
        
        userGroup.setName("Users");
        userGroup.setDescription("User group");
        
        assertThat(adminGroup.getName()).isEqualTo("Admins");
        assertThat(userGroup.getName()).isEqualTo("Users");
        assertThat(adminGroup.getDescription()).isEqualTo("Administrator group");
        assertThat(userGroup.getDescription()).isEqualTo("User group");
    }

    @Test
    void testSerialVersionUID() {
        assertThat(TurGroup.class)
                .hasDeclaredFields("serialVersionUID");
    }
}
