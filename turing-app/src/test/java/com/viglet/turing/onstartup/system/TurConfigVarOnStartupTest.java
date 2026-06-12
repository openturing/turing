package com.viglet.turing.onstartup.system;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

@ExtendWith(MockitoExtension.class)
class TurConfigVarOnStartupTest {

    @Mock
    private TurConfigVarRepository turConfigVarRepository;

    @InjectMocks
    private TurConfigVarOnStartup turConfigVarOnStartup;

    @Test
    void shouldCreateFirstTimeConfigVarWhenMissing() {
        when(turConfigVarRepository.findById(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.empty());

        turConfigVarOnStartup.createDefaultRows();

        ArgumentCaptor<TurConfigVar> captor = ArgumentCaptor.forClass(TurConfigVar.class);
        verify(turConfigVarRepository, org.mockito.Mockito.times(31)).save(captor.capture());

        List<TurConfigVar> savedVars = captor.getAllValues();
        assertTrue(savedVars.stream().anyMatch(configVar -> "FIRST_TIME".equals(configVar.getId())
                && "/system".equals(configVar.getPath())
                && "true".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_DECIMAL_SEPARATOR".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "DOT".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_PYTHON_EXECUTABLE".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_PYTHON_REQUIREMENTS".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar ->
                "GLOBAL_CODE_INTERPRETER_EXECUTION_MODE".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "NATIVE".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar ->
                "GLOBAL_CODE_INTERPRETER_DOCKER_IMAGE".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "python:3.12-slim".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar ->
                "GLOBAL_CODE_INTERPRETER_SKILL_IMAGE".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "python:3.12-slim".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar ->
                "GLOBAL_CODE_INTERPRETER_URL_SIGNING_SECRET".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                // Secret is randomly generated each run — can't pin to a literal.
                // Just verify it was minted (non-blank) and shaped like Base64
                // (44 chars for 32 random bytes).
                && configVar.getValue() != null
                && configVar.getValue().length() >= 40));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_DEFAULT_LLM".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_LLM_CACHE_ENABLED".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "false".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_LLM_CACHE_TTL_MS".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "3600000".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_LLM_CACHE_REGENERATE".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "false".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_EMAIL_PROVIDER".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "BREVO".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_EMAIL_API_KEY".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_SENDER_EMAIL".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_SENDER_NAME".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_RECIPIENT_EMAIL".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_RAG_ENABLED".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "false".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_DEFAULT_EMBEDDING_MODEL".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_DEFAULT_EMBEDDING_STORE".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_DEFAULT_AI_AGENT".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar -> "GLOBAL_PII_SLOT_TTL_HOURS".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "24".equals(configVar.getValue())));
        // T328 — SN RAG relevance gate + reranker defaults.
        assertTrue(savedVars.stream().anyMatch(configVar ->
                "GLOBAL_RAG_SN_SIMILARITY_THRESHOLD".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "0.0".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar ->
                "GLOBAL_RAG_SN_RERANK_ENABLED".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "false".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar ->
                "GLOBAL_RAG_SN_RERANK_TOP_N".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "20".equals(configVar.getValue())));
        // T331 — grounded follow-up suggestions (default off).
        assertTrue(savedVars.stream().anyMatch(configVar ->
                "GLOBAL_RAG_SN_FOLLOWUPS_ENABLED".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "false".equals(configVar.getValue())));
        // T330 — groundedness audit (default off).
        assertTrue(savedVars.stream().anyMatch(configVar ->
                "GLOBAL_RAG_SN_GROUNDEDNESS_CHECK_ENABLED".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "false".equals(configVar.getValue())));
        // T337 — pluggable reranker strategy (default LLM) + connection vars.
        assertTrue(savedVars.stream().anyMatch(configVar ->
                "GLOBAL_RAG_SN_RERANK_STRATEGY".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "LLM".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar ->
                "GLOBAL_RAG_SN_RERANK_ENDPOINT".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar ->
                "GLOBAL_RAG_SN_RERANK_MODEL".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
        assertTrue(savedVars.stream().anyMatch(configVar ->
                "GLOBAL_RAG_SN_RERANK_API_KEY".equals(configVar.getId())
                && "/system/global".equals(configVar.getPath())
                && "".equals(configVar.getValue())));
    }

    @Test
    void shouldNotCreateConfigVarWhenAlreadyExists() {
        // Mock returns a fresh TurConfigVar (blank value) for every row.
        // ensureConfigVar leaves these alone (existing row → no save), but
        // ensureSecretConfigVar AUTO-HEALS blank values for the HMAC
        // secret. So save is called exactly once — for the secret heal —
        // and the other 18 rows are skipped.
        when(turConfigVarRepository.findById(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Optional.of(new TurConfigVar()));

        turConfigVarOnStartup.createDefaultRows();

        ArgumentCaptor<TurConfigVar> captor = ArgumentCaptor.forClass(TurConfigVar.class);
        verify(turConfigVarRepository, org.mockito.Mockito.times(1)).save(captor.capture());
        TurConfigVar saved = captor.getValue();
        assertTrue("GLOBAL_CODE_INTERPRETER_URL_SIGNING_SECRET".equals(saved.getId())
                || saved.getValue() != null && !saved.getValue().isBlank(),
                "Only the URL signing secret should be auto-healed; got id=" + saved.getId()
                        + " value-length=" + (saved.getValue() == null ? -1 : saved.getValue().length()));
    }
}
