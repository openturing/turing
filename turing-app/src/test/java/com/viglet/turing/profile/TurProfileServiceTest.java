/*
 * Copyright (C) 2016-2026 the original author or authors.
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
package com.viglet.turing.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import com.viglet.turing.api.profile.bean.TurProfileDto;
import com.viglet.turing.api.profile.bean.TurProfileRequest;
import com.viglet.turing.persistence.model.profile.TurProfile;
import com.viglet.turing.persistence.repository.profile.TurProfileRepository;

@ExtendWith(MockitoExtension.class)
class TurProfileServiceTest {

    @Mock
    private TurProfileRepository turProfileRepository;

    @InjectMocks
    private TurProfileService service;

    private static TurProfile profile(String id, String name, boolean isDefault) {
        return TurProfile.builder().id(id).name(name).defaultProfile(isDefault).build();
    }

    @Test
    void ensureDefaultProfileCreatesPadraoWhenAbsent() {
        when(turProfileRepository.findByDefaultProfileTrue()).thenReturn(Optional.empty());
        when(turProfileRepository.findByNameIgnoreCase(TurProfileService.DEFAULT_PROFILE_NAME))
                .thenReturn(Optional.empty());
        when(turProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TurProfile result = service.ensureDefaultProfile();

        assertThat(result.getName()).isEqualTo(TurProfileService.DEFAULT_PROFILE_NAME);
        assertThat(result.isDefaultProfile()).isTrue();
    }

    @Test
    void ensureDefaultProfileIsIdempotentWhenDefaultExists() {
        TurProfile existing = profile("p", "Padrão", true);
        when(turProfileRepository.findByDefaultProfileTrue()).thenReturn(Optional.of(existing));

        assertThat(service.ensureDefaultProfile()).isSameAs(existing);
        verify(turProfileRepository, never()).save(any());
    }

    @Test
    void listEnsuresDefaultAndSortsDefaultFirst() {
        TurProfile padrao = profile("pd", "Padrão", true);
        TurProfile cursos = profile("c", "Cursos EE", false);
        when(turProfileRepository.findByDefaultProfileTrue()).thenReturn(Optional.of(padrao));
        when(turProfileRepository.findAll()).thenReturn(List.of(cursos, padrao));

        List<TurProfileDto> result = service.list();

        assertThat(result).extracting(TurProfileDto::id).containsExactly("pd", "c");
        assertThat(result.get(0).defaultProfile()).isTrue();
    }

    @Test
    void createRejectsDuplicateNameGlobally() {
        when(turProfileRepository.existsByNameIgnoreCase("Cursos EE")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new TurProfileRequest("Cursos EE", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
        verify(turProfileRepository, never()).save(any());
    }

    @Test
    void createFirstProfileBecomesDefault() {
        when(turProfileRepository.existsByNameIgnoreCase("Solo")).thenReturn(false);
        when(turProfileRepository.findByDefaultProfileTrue()).thenReturn(Optional.empty());
        when(turProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TurProfileDto result = service.create(new TurProfileRequest("Solo", null));

        assertThat(result.defaultProfile()).isTrue();
    }

    @Test
    void createNonFirstProfileIsNotDefault() {
        when(turProfileRepository.existsByNameIgnoreCase("Cursos EE")).thenReturn(false);
        when(turProfileRepository.findByDefaultProfileTrue())
                .thenReturn(Optional.of(profile("pd", "Padrão", true)));
        when(turProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TurProfileDto result = service.create(new TurProfileRequest("Cursos EE", "por aba"));

        assertThat(result.defaultProfile()).isFalse();
        assertThat(result.description()).isEqualTo("por aba");
    }

    @Test
    void updateRejectsRenamingDefaultProfile() {
        TurProfile padrao = profile("pd", "Padrão", true);
        when(turProfileRepository.findById("pd")).thenReturn(Optional.of(padrao));

        assertThatThrownBy(() -> service.update("pd", new TurProfileRequest("Outro", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("renamed");
        verify(turProfileRepository, never()).save(any());
    }

    @Test
    void updateAllowsDescriptionChangeOnDefaultWhenNameUnchanged() {
        TurProfile padrao = profile("pd", "Padrão", true);
        when(turProfileRepository.findById("pd")).thenReturn(Optional.of(padrao));
        when(turProfileRepository.findByNameIgnoreCase("Padrão")).thenReturn(Optional.of(padrao));
        when(turProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        TurProfileDto result = service.update("pd", new TurProfileRequest("Padrão", "Fallback"));

        assertThat(result.name()).isEqualTo("Padrão");
        assertThat(result.description()).isEqualTo("Fallback");
    }

    @Test
    void updateRejectsDuplicateNameOnRename() {
        TurProfile cursos = profile("c", "Cursos EE", false);
        TurProfile other = profile("o", "Home", false);
        when(turProfileRepository.findById("c")).thenReturn(Optional.of(cursos));
        when(turProfileRepository.findByNameIgnoreCase("Home")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update("c", new TurProfileRequest("Home", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void setDefaultSwapsTheDefaultFlag() {
        TurProfile padrao = profile("pd", "Padrão", true);
        TurProfile cursos = profile("c", "Cursos EE", false);
        when(turProfileRepository.findById("c")).thenReturn(Optional.of(cursos));
        when(turProfileRepository.findAll()).thenReturn(List.of(padrao, cursos));
        when(turProfileRepository.findByDefaultProfileTrue()).thenReturn(Optional.of(cursos));
        when(turProfileRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.setDefault("c");

        assertThat(cursos.isDefaultProfile()).isTrue();
        assertThat(padrao.isDefaultProfile()).isFalse();
        verify(turProfileRepository).save(padrao);
        verify(turProfileRepository).save(cursos);
    }

    @Test
    void deleteRejectsDefaultProfile() {
        TurProfile padrao = profile("pd", "Padrão", true);
        when(turProfileRepository.findById("pd")).thenReturn(Optional.of(padrao));

        assertThatThrownBy(() -> service.delete("pd"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("default");
        verify(turProfileRepository, never()).delete(any(TurProfile.class));
    }

    @Test
    void deleteRemovesNonDefaultProfile() {
        TurProfile cursos = profile("c", "Cursos EE", false);
        when(turProfileRepository.findById("c")).thenReturn(Optional.of(cursos));

        service.delete("c");

        verify(turProfileRepository).delete(cursos);
    }

    @Test
    void createRejectsBlankName() {
        assertThatThrownBy(() -> service.create(new TurProfileRequest("   ", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("required");
    }
}
