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
 * Unit tests for TurRole.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurRoleTest {

    @Test
    void testNoArgsConstructor() {
        TurRole turRole = new TurRole();
        
        assertThat(turRole).isNotNull();
        assertThat(turRole.getId()).isNull();
        assertThat(turRole.getName()).isNull();
        assertThat(turRole.getDescription()).isNull();
        assertThat(turRole.getTurGroups()).isEmpty();
        assertThat(turRole.getTurPrivileges()).isEmpty();
    }

    @Test
    void testConstructorWithName() {
        TurRole turRole = new TurRole("ADMIN");
        
        assertThat(turRole.getName()).isEqualTo("ADMIN");
        assertThat(turRole.getId()).isNull();
        assertThat(turRole.getDescription()).isNull();
        assertThat(turRole.getTurGroups()).isEmpty();
        assertThat(turRole.getTurPrivileges()).isEmpty();
    }

    @Test
    void testGettersAndSetters() {
        TurRole turRole = new TurRole();
        
        turRole.setId("role-id-123");
        turRole.setName("USER");
        turRole.setDescription("Standard User Role");
        
        assertThat(turRole.getId()).isEqualTo("role-id-123");
        assertThat(turRole.getName()).isEqualTo("USER");
        assertThat(turRole.getDescription()).isEqualTo("Standard User Role");
    }

    @Test
    void testSetTurPrivilegesWithValidCollection() {
        TurRole turRole = new TurRole();
        TurPrivilege privilege1 = new TurPrivilege("READ");
        TurPrivilege privilege2 = new TurPrivilege("WRITE");
        Collection<TurPrivilege> privileges = Arrays.asList(privilege1, privilege2);
        
        turRole.setTurPrivileges(privileges);
        
        assertThat(turRole.getTurPrivileges()).hasSize(2);
        assertThat(turRole.getTurPrivileges()).containsExactlyInAnyOrder(privilege1, privilege2);
    }

    @Test
    void testSetTurPrivilegesWithNull() {
        TurRole turRole = new TurRole();
        TurPrivilege privilege = new TurPrivilege("ADMIN");
        turRole.setTurPrivileges(Arrays.asList(privilege));
        
        assertThat(turRole.getTurPrivileges()).hasSize(1);
        
        turRole.setTurPrivileges(null);
        
        assertThat(turRole.getTurPrivileges()).isEmpty();
    }

    @Test
    void testSetTurPrivilegesReplacesExisting() {
        TurRole turRole = new TurRole();
        TurPrivilege privilege1 = new TurPrivilege("READ");
        TurPrivilege privilege2 = new TurPrivilege("WRITE");
        TurPrivilege privilege3 = new TurPrivilege("EXECUTE");
        
        turRole.setTurPrivileges(Arrays.asList(privilege1, privilege2));
        assertThat(turRole.getTurPrivileges()).hasSize(2);
        
        turRole.setTurPrivileges(Arrays.asList(privilege3));
        assertThat(turRole.getTurPrivileges()).hasSize(1);
        assertThat(turRole.getTurPrivileges()).containsExactly(privilege3);
    }

    @Test
    void testGetTurGroupsInitialized() {
        TurRole turRole = new TurRole();
        
        assertThat(turRole.getTurGroups()).isNotNull();
        assertThat(turRole.getTurGroups()).isEmpty();
    }

    @Test
    void testSetNameWithDifferentValues() {
        TurRole turRole = new TurRole();
        
        turRole.setName("ADMIN");
        assertThat(turRole.getName()).isEqualTo("ADMIN");
        
        turRole.setName("MODERATOR");
        assertThat(turRole.getName()).isEqualTo("MODERATOR");
        
        turRole.setName("GUEST");
        assertThat(turRole.getName()).isEqualTo("GUEST");
    }

    @Test
    void testSetDescriptionWithNullValue() {
        TurRole turRole = new TurRole("USER");
        turRole.setDescription("Initial description");
        
        assertThat(turRole.getDescription()).isEqualTo("Initial description");
        
        turRole.setDescription(null);
        
        assertThat(turRole.getDescription()).isNull();
    }

    @Test
    void testSetIdWithNullValue() {
        TurRole turRole = new TurRole();
        turRole.setId("test-id");
        
        assertThat(turRole.getId()).isEqualTo("test-id");
        
        turRole.setId(null);
        
        assertThat(turRole.getId()).isNull();
    }

    @Test
    void testMultipleRolesIndependence() {
        TurRole adminRole = new TurRole("ADMIN");
        TurRole userRole = new TurRole("USER");
        
        adminRole.setDescription("Administrator role");
        userRole.setDescription("User role");
        
        assertThat(adminRole.getName()).isEqualTo("ADMIN");
        assertThat(userRole.getName()).isEqualTo("USER");
        assertThat(adminRole.getDescription()).isEqualTo("Administrator role");
        assertThat(userRole.getDescription()).isEqualTo("User role");
    }

    @Test
    void testSerialVersionUID() {
        assertThat(TurRole.class)
                .hasDeclaredFields("serialVersionUID");
    }
}
