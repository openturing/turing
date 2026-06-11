/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Locale;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.rag.TurRagBm25Core;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.repository.rag.TurRagBm25CoreRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

/**
 * Unit tests for {@link TurRagBm25CoreProvisioner}. Pure JPA + plugin
 * mocks; no Spring context, no real SE. Pins the lifecycle invariants:
 * idempotency, error capture, naming convention.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
@ExtendWith(MockitoExtension.class)
class TurRagBm25CoreProvisionerTest {

    @Mock
    private TurRagBm25CoreRepository coreRepository;
    @Mock
    private TurSearchEnginePluginFactory pluginFactory;
    @Mock
    private TurSearchEnginePlugin plugin;

    private TurRagBm25CoreProvisioner provisioner;

    private TurStoreInstance store;
    private TurSEInstance seInstance;

    @BeforeEach
    void setUp() {
        com.viglet.turing.properties.TurConfigProperties props =
                new com.viglet.turing.properties.TurConfigProperties();
        com.viglet.turing.tenant.TurTenantCoreNaming coreNaming =
                new com.viglet.turing.tenant.TurTenantCoreNaming(
                        new com.viglet.turing.tenant.TurTenantContext(props));
        provisioner = new TurRagBm25CoreProvisioner(coreRepository, pluginFactory, coreNaming);
        store = new TurStoreInstance();
        store.setId("a3b2c1d4-9876-5432-1098-abcdef012345");
        seInstance = new TurSEInstance();
        seInstance.setId("se-1");
    }

    // ── coreNameFor (pure function) ──

    @Test
    void coreNameFor_buildsCanonicalName() {
        assertThat(TurRagBm25CoreProvisioner.coreNameFor(store, Locale.forLanguageTag("pt-BR")))
                .isEqualTo("rag_a3b2c1d4_pt-BR");
        assertThat(TurRagBm25CoreProvisioner.coreNameFor(store, Locale.forLanguageTag("en-US")))
                .isEqualTo("rag_a3b2c1d4_en-US");
    }

    @Test
    void coreNameFor_nullInputs_throw() {
        assertThatThrownBy(() -> TurRagBm25CoreProvisioner.coreNameFor(null, Locale.US))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TurRagBm25CoreProvisioner.coreNameFor(store, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── provision: first time ──

    @Test
    void provision_firstTime_createsRowAndCallsCreateStandaloneIndex() {
        when(coreRepository.findByTurStoreInstance_IdAndLocale(eq(store.getId()), any(Locale.class)))
                .thenReturn(Optional.empty());
        when(coreRepository.save(any(TurRagBm25Core.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.indexExists(eq(seInstance), anyString())).thenReturn(false);
        when(plugin.getPluginType()).thenReturn("solr");

        TurRagBm25Core core = provisioner.provision(store, Locale.forLanguageTag("pt-BR"), seInstance);

        assertThat(core.getStatus()).isEqualTo(TurRagBm25Core.Status.PROVISIONED);
        assertThat(core.getCoreName()).isEqualTo("rag_a3b2c1d4_pt-BR");
        assertThat(core.getLastError()).isNull();
        verify(plugin).createStandaloneIndex(eq(seInstance), eq(Locale.forLanguageTag("pt-BR")),
                eq("rag_a3b2c1d4_pt-BR"));
        // Saved twice: once on PROVISIONING transition, once on PROVISIONED.
        verify(coreRepository, times(2)).save(any(TurRagBm25Core.class));
    }

    @Test
    void provision_existingProvisionedRow_skipsIndexCreationWhenSeAlreadyHasIt() {
        TurRagBm25Core existing = newRow(TurRagBm25Core.Status.PROVISIONED);
        when(coreRepository.findByTurStoreInstance_IdAndLocale(eq(store.getId()), any(Locale.class)))
                .thenReturn(Optional.of(existing));
        when(coreRepository.save(any(TurRagBm25Core.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.indexExists(seInstance, "rag_a3b2c1d4_pt-BR")).thenReturn(true);

        TurRagBm25Core core = provisioner.provision(store, Locale.forLanguageTag("pt-BR"), seInstance);

        assertThat(core.getStatus()).isEqualTo(TurRagBm25Core.Status.PROVISIONED);
        verify(plugin, never()).createStandaloneIndex(any(), any(), anyString());
    }

    @Test
    void provision_existingErrorRow_retriesIndexCreation() {
        TurRagBm25Core errored = newRow(TurRagBm25Core.Status.ERROR);
        errored.setLastError("previous failure");
        when(coreRepository.findByTurStoreInstance_IdAndLocale(eq(store.getId()), any(Locale.class)))
                .thenReturn(Optional.of(errored));
        when(coreRepository.save(any(TurRagBm25Core.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.indexExists(eq(seInstance), anyString())).thenReturn(false);
        when(plugin.getPluginType()).thenReturn("solr");

        TurRagBm25Core core = provisioner.provision(store, Locale.forLanguageTag("pt-BR"), seInstance);

        assertThat(core.getStatus()).isEqualTo(TurRagBm25Core.Status.PROVISIONED);
        // lastError cleared on success.
        assertThat(core.getLastError()).isNull();
        verify(plugin).createStandaloneIndex(eq(seInstance), any(), anyString());
    }

    @Test
    void provision_deletingRow_throws() {
        TurRagBm25Core deleting = newRow(TurRagBm25Core.Status.DELETING);
        when(coreRepository.findByTurStoreInstance_IdAndLocale(eq(store.getId()), any(Locale.class)))
                .thenReturn(Optional.of(deleting));

        assertThatThrownBy(() -> provisioner.provision(store, Locale.forLanguageTag("pt-BR"), seInstance))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("being deleted");
        verify(plugin, never()).createStandaloneIndex(any(), any(), anyString());
    }

    @Test
    void provision_pluginThrows_capturesErrorAndPersistsStatus() {
        when(coreRepository.findByTurStoreInstance_IdAndLocale(eq(store.getId()), any(Locale.class)))
                .thenReturn(Optional.empty());
        when(coreRepository.save(any(TurRagBm25Core.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pluginFactory.getPluginForInstance(seInstance)).thenReturn(plugin);
        when(plugin.indexExists(eq(seInstance), anyString())).thenReturn(false);
        when(plugin.getPluginType()).thenReturn("solr");
        doThrowFromCreate(new RuntimeException("Solr is down"));

        assertThatThrownBy(() -> provisioner.provision(store, Locale.forLanguageTag("pt-BR"), seInstance))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Solr is down");

        ArgumentCaptor<TurRagBm25Core> captor = ArgumentCaptor.forClass(TurRagBm25Core.class);
        verify(coreRepository, times(2)).save(captor.capture());
        TurRagBm25Core lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertThat(lastSaved.getStatus()).isEqualTo(TurRagBm25Core.Status.ERROR);
        assertThat(lastSaved.getLastError()).contains("Solr is down");
    }

    // ── deprovision ──

    @Test
    void deprovision_existingRow_callsDeleteIndexAndRemovesRow() {
        TurRagBm25Core core = newRow(TurRagBm25Core.Status.PROVISIONED);
        when(coreRepository.findByTurStoreInstance_IdAndLocale(eq(store.getId()), any(Locale.class)))
                .thenReturn(Optional.of(core));
        when(coreRepository.save(any(TurRagBm25Core.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pluginFactory.getPluginForInstance(any(TurSEInstance.class))).thenReturn(plugin);

        provisioner.deprovision(store, Locale.forLanguageTag("pt-BR"));

        verify(plugin).deleteIndex(any(), eq("rag_a3b2c1d4_pt-BR"));
        verify(coreRepository).delete(core);
    }

    @Test
    void deprovision_missingRow_noOp() {
        when(coreRepository.findByTurStoreInstance_IdAndLocale(eq(store.getId()), any(Locale.class)))
                .thenReturn(Optional.empty());

        provisioner.deprovision(store, Locale.forLanguageTag("pt-BR"));

        verify(plugin, never()).deleteIndex(any(), anyString());
        verify(coreRepository, never()).delete(any(TurRagBm25Core.class));
    }

    // ── helpers ──

    private TurRagBm25Core newRow(TurRagBm25Core.Status status) {
        TurRagBm25Core core = new TurRagBm25Core();
        core.setId("core-1");
        core.setTurStoreInstance(store);
        core.setTurSEInstance(seInstance);
        core.setLocale(Locale.forLanguageTag("pt-BR"));
        core.setCoreName("rag_a3b2c1d4_pt-BR");
        core.setStatus(status);
        return core;
    }

    private void doThrowFromCreate(RuntimeException re) {
        org.mockito.Mockito.doThrow(re).when(plugin)
                .createStandaloneIndex(any(), any(), anyString());
    }
}
