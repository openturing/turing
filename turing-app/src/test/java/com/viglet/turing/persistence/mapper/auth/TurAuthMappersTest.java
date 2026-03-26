package com.viglet.turing.persistence.mapper.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.dto.auth.TurGroupDto;
import com.viglet.turing.persistence.dto.auth.TurPrivilegeDto;
import com.viglet.turing.persistence.dto.auth.TurRoleDto;
import com.viglet.turing.persistence.dto.auth.TurUserDto;
import com.viglet.turing.persistence.model.auth.TurGroup;
import com.viglet.turing.persistence.model.auth.TurPrivilege;
import com.viglet.turing.persistence.model.auth.TurRole;
import com.viglet.turing.persistence.model.auth.TurUser;

/**
 * Tests for auth mapper implementations (User, Group, Role, Privilege).
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurAuthMappersTest {

    // ---- TurUserMapper -------------------------------------------------------

    @Nested
    class UserMapperTests {

        private TurUserMapperImpl mapper;

        @BeforeEach
        void setUp() {
            mapper = new TurUserMapperImpl();
        }

        private TurUser buildUser() {
            return TurUser.builder()
                    .username("admin")
                    .email("admin@example.com")
                    .firstName("Admin")
                    .lastName("User")
                    .password("secret")
                    .realm("local")
                    .enabled(1)
                    .avatarUrl("https://example.com/avatar.png")
                    .lastLogin(Instant.parse("2026-03-26T10:00:00Z"))
                    .build();
        }

        @Test
        void shouldMapEntityToDto() {
            TurUser user = buildUser();

            TurUserDto dto = mapper.toDto(user);

            assertNotNull(dto);
            assertEquals("admin", dto.getUsername());
            assertEquals("admin@example.com", dto.getEmail());
            assertEquals("Admin", dto.getFirstName());
            assertEquals("User", dto.getLastName());
            assertEquals("secret", dto.getPassword());
            assertEquals("local", dto.getRealm());
            assertEquals(1, dto.getEnabled());
            assertEquals("https://example.com/avatar.png", dto.getAvatarUrl());
            assertEquals(Instant.parse("2026-03-26T10:00:00Z"), dto.getLastLogin());
        }

        @Test
        void shouldReturnNullWhenEntityIsNull() {
            assertNull(mapper.toDto(null));
        }

        @Test
        void shouldMapDtoToEntity() {
            TurUserDto dto = new TurUserDto();
            dto.setUsername("user1");
            dto.setEmail("user1@example.com");
            dto.setFirstName("First");
            dto.setLastName("Last");
            dto.setEnabled(1);

            TurUser entity = mapper.toEntity(dto);

            assertNotNull(entity);
            assertEquals("user1", entity.getUsername());
            assertEquals("user1@example.com", entity.getEmail());
            assertEquals("First", entity.getFirstName());
        }

        @Test
        void shouldReturnNullWhenDtoIsNull() {
            assertNull(mapper.toEntity(null));
        }

        @Test
        void shouldMapEntityListToDtoList() {
            List<TurUser> users = List.of(buildUser());

            List<TurUserDto> dtos = mapper.toDtoList(users);

            assertNotNull(dtos);
            assertEquals(1, dtos.size());
            assertEquals("admin", dtos.get(0).getUsername());
        }

        @Test
        void shouldReturnNullForNullList() {
            assertNull(mapper.toDtoList(null));
        }

        @Test
        void shouldMapEntitySetToDtoSet() {
            Set<TurUser> users = Set.of(buildUser());

            Set<TurUserDto> dtos = mapper.toDtoSet(users);

            assertNotNull(dtos);
            assertEquals(1, dtos.size());
        }

        @Test
        void shouldReturnNullForNullSet() {
            assertNull(mapper.toDtoSet(null));
        }

        @Test
        void shouldHandleEntityWithNullFields() {
            TurUser user = new TurUser();
            user.setUsername("nulluser");

            TurUserDto dto = mapper.toDto(user);

            assertNotNull(dto);
            assertEquals("nulluser", dto.getUsername());
            assertNull(dto.getEmail());
            assertNull(dto.getFirstName());
        }
    }

    // ---- TurGroupMapper ------------------------------------------------------

    @Nested
    class GroupMapperTests {

        private TurGroupMapperImpl mapper;

        @BeforeEach
        void setUp() {
            mapper = new TurGroupMapperImpl();
        }

        private TurGroup buildGroup() {
            TurGroup group = new TurGroup();
            group.setId("grp-1");
            group.setName("Admins");
            group.setDescription("Administrator group");
            return group;
        }

        @Test
        void shouldMapEntityToDto() {
            TurGroup group = buildGroup();

            TurGroupDto dto = mapper.toDto(group);

            assertNotNull(dto);
            assertEquals("grp-1", dto.getId());
            assertEquals("Admins", dto.getName());
            assertEquals("Administrator group", dto.getDescription());
        }

        @Test
        void shouldReturnNullWhenEntityIsNull() {
            assertNull(mapper.toDto(null));
        }

        @Test
        void shouldMapDtoToEntity() {
            TurGroupDto dto = new TurGroupDto();
            dto.setId("grp-2");
            dto.setName("Users");
            dto.setDescription("Regular users");

            TurGroup entity = mapper.toEntity(dto);

            assertNotNull(entity);
            assertEquals("grp-2", entity.getId());
            assertEquals("Users", entity.getName());
            assertEquals("Regular users", entity.getDescription());
        }

        @Test
        void shouldReturnNullWhenDtoIsNull() {
            assertNull(mapper.toEntity(null));
        }

        @Test
        void shouldMapEntityListToDtoList() {
            List<TurGroup> groups = List.of(buildGroup());

            List<TurGroupDto> dtos = mapper.toDtoList(groups);

            assertNotNull(dtos);
            assertEquals(1, dtos.size());
            assertEquals("grp-1", dtos.get(0).getId());
        }

        @Test
        void shouldReturnNullForNullList() {
            assertNull(mapper.toDtoList(null));
        }

        @Test
        void shouldMapEntitySetToDtoSet() {
            Set<TurGroup> groups = Set.of(buildGroup());

            Set<TurGroupDto> dtos = mapper.toDtoSet(groups);

            assertNotNull(dtos);
            assertEquals(1, dtos.size());
        }

        @Test
        void shouldReturnNullForNullSet() {
            assertNull(mapper.toDtoSet(null));
        }

        @Test
        void shouldHandleEntityWithNullFields() {
            TurGroup group = new TurGroup();
            group.setId("null-grp");

            TurGroupDto dto = mapper.toDto(group);

            assertNotNull(dto);
            assertEquals("null-grp", dto.getId());
            assertNull(dto.getName());
        }
    }

    // ---- TurRoleMapper -------------------------------------------------------

    @Nested
    class RoleMapperTests {

        private TurRoleMapperImpl mapper;

        @BeforeEach
        void setUp() {
            mapper = new TurRoleMapperImpl();
        }

        private TurRole buildRole() {
            TurRole role = new TurRole("ROLE_ADMIN");
            role.setId("role-1");
            role.setDescription("Admin role");
            return role;
        }

        @Test
        void shouldMapEntityToDto() {
            TurRole role = buildRole();

            TurRoleDto dto = mapper.toDto(role);

            assertNotNull(dto);
            assertEquals("role-1", dto.getId());
            assertEquals("ROLE_ADMIN", dto.getName());
            assertEquals("Admin role", dto.getDescription());
        }

        @Test
        void shouldReturnNullWhenEntityIsNull() {
            assertNull(mapper.toDto(null));
        }

        @Test
        void shouldMapDtoToEntity() {
            TurRoleDto dto = new TurRoleDto();
            dto.setId("role-2");
            dto.setName("ROLE_USER");
            dto.setDescription("User role");

            TurRole entity = mapper.toEntity(dto);

            assertNotNull(entity);
            assertEquals("role-2", entity.getId());
            assertEquals("ROLE_USER", entity.getName());
        }

        @Test
        void shouldReturnNullWhenDtoIsNull() {
            assertNull(mapper.toEntity(null));
        }

        @Test
        void shouldMapEntityListToDtoList() {
            List<TurRole> roles = List.of(buildRole());

            List<TurRoleDto> dtos = mapper.toDtoList(roles);

            assertNotNull(dtos);
            assertEquals(1, dtos.size());
            assertEquals("role-1", dtos.get(0).getId());
        }

        @Test
        void shouldReturnNullForNullList() {
            assertNull(mapper.toDtoList(null));
        }

        @Test
        void shouldMapEntitySetToDtoSet() {
            Set<TurRole> roles = Set.of(buildRole());

            Set<TurRoleDto> dtos = mapper.toDtoSet(roles);

            assertNotNull(dtos);
            assertEquals(1, dtos.size());
        }

        @Test
        void shouldReturnNullForNullSet() {
            assertNull(mapper.toDtoSet(null));
        }
    }

    // ---- TurPrivilegeMapper --------------------------------------------------

    @Nested
    class PrivilegeMapperTests {

        private TurPrivilegeMapperImpl mapper;

        @BeforeEach
        void setUp() {
            mapper = new TurPrivilegeMapperImpl();
        }

        private TurPrivilege buildPrivilege() {
            TurPrivilege priv = new TurPrivilege("READ_PRIVILEGE");
            priv.setId("priv-1");
            priv.setDescription("Read access");
            priv.setCategory("search");
            return priv;
        }

        @Test
        void shouldMapEntityToDto() {
            TurPrivilege priv = buildPrivilege();

            TurPrivilegeDto dto = mapper.toDto(priv);

            assertNotNull(dto);
            assertEquals("priv-1", dto.getId());
            assertEquals("READ_PRIVILEGE", dto.getName());
            assertEquals("Read access", dto.getDescription());
            assertEquals("search", dto.getCategory());
        }

        @Test
        void shouldReturnNullWhenEntityIsNull() {
            assertNull(mapper.toDto(null));
        }

        @Test
        void shouldMapDtoToEntity() {
            TurPrivilegeDto dto = new TurPrivilegeDto();
            dto.setId("priv-2");
            dto.setName("WRITE_PRIVILEGE");
            dto.setDescription("Write access");
            dto.setCategory("admin");

            TurPrivilege entity = mapper.toEntity(dto);

            assertNotNull(entity);
            assertEquals("priv-2", entity.getId());
            assertEquals("WRITE_PRIVILEGE", entity.getName());
            assertEquals("Write access", entity.getDescription());
            assertEquals("admin", entity.getCategory());
        }

        @Test
        void shouldReturnNullWhenDtoIsNull() {
            assertNull(mapper.toEntity(null));
        }

        @Test
        void shouldMapEntityListToDtoList() {
            List<TurPrivilege> privs = List.of(buildPrivilege());

            List<TurPrivilegeDto> dtos = mapper.toDtoList(privs);

            assertNotNull(dtos);
            assertEquals(1, dtos.size());
            assertEquals("priv-1", dtos.get(0).getId());
        }

        @Test
        void shouldReturnNullForNullList() {
            assertNull(mapper.toDtoList(null));
        }

        @Test
        void shouldMapEntitySetToDtoSet() {
            Set<TurPrivilege> privs = Set.of(buildPrivilege());

            Set<TurPrivilegeDto> dtos = mapper.toDtoSet(privs);

            assertNotNull(dtos);
            assertEquals(1, dtos.size());
        }

        @Test
        void shouldReturnNullForNullSet() {
            assertNull(mapper.toDtoSet(null));
        }

        @Test
        void shouldHandleEntityWithNullFields() {
            TurPrivilege priv = new TurPrivilege();
            priv.setId("null-priv");

            TurPrivilegeDto dto = mapper.toDto(priv);

            assertNotNull(dto);
            assertEquals("null-priv", dto.getId());
            assertNull(dto.getName());
            assertNull(dto.getDescription());
        }
    }
}
