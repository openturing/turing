package com.viglet.turing.onstartup.system;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
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

    private static final String GLOBAL = "/system/global";

    @Mock
    private TurConfigVarRepository turConfigVarRepository;

    @InjectMocks
    private TurConfigVarOnStartup turConfigVarOnStartup;

    /** Runs the first-time bootstrap (no existing rows) and returns every saved config var. */
    private List<TurConfigVar> savedDefaults() {
        when(turConfigVarRepository.findById(anyString())).thenReturn(Optional.empty());
        turConfigVarOnStartup.createDefaultRows();
        ArgumentCaptor<TurConfigVar> captor = ArgumentCaptor.forClass(TurConfigVar.class);
        verify(turConfigVarRepository, times(50)).save(captor.capture());
        return captor.getAllValues();
    }

    /** Fails unless a {@code (id, path, value)} row was saved. */
    private static void expectVar(List<TurConfigVar> savedVars, String id, String path, String value) {
        assertTrue(savedVars.stream().anyMatch(configVar ->
                        id.equals(configVar.getId())
                        && path.equals(configVar.getPath())
                        && value.equals(configVar.getValue())),
                () -> "Expected config var " + id + " at " + path + " = '" + value + "'");
    }

    @Test
    void shouldSaveSystemAndCodeInterpreterDefaults() {
        List<TurConfigVar> savedVars = savedDefaults();
        expectVar(savedVars, "FIRST_TIME", "/system", "true");
        expectVar(savedVars, "GLOBAL_DECIMAL_SEPARATOR", GLOBAL, "DOT");
        expectVar(savedVars, "GLOBAL_PYTHON_EXECUTABLE", GLOBAL, "");
        expectVar(savedVars, "GLOBAL_PYTHON_REQUIREMENTS", GLOBAL, "");
        expectVar(savedVars, "GLOBAL_CODE_INTERPRETER_EXECUTION_MODE", GLOBAL, "NATIVE");
        expectVar(savedVars, "GLOBAL_CODE_INTERPRETER_DOCKER_IMAGE", GLOBAL, "python:3.12-slim");
        expectVar(savedVars, "GLOBAL_CODE_INTERPRETER_SKILL_IMAGE", GLOBAL, "python:3.12-slim");
        // Secret is randomly generated each run — can't pin to a literal. Just
        // verify it was minted (non-blank) and shaped like Base64 (≥40 chars).
        assertTrue(savedVars.stream().anyMatch(configVar ->
                "GLOBAL_CODE_INTERPRETER_URL_SIGNING_SECRET".equals(configVar.getId())
                && GLOBAL.equals(configVar.getPath())
                && configVar.getValue() != null
                && configVar.getValue().length() >= 40));
    }

    @Test
    void shouldSaveLlmEmailAndModelDefaults() {
        List<TurConfigVar> savedVars = savedDefaults();
        expectVar(savedVars, "GLOBAL_DEFAULT_LLM", GLOBAL, "");
        expectVar(savedVars, "GLOBAL_LLM_CACHE_ENABLED", GLOBAL, "false");
        expectVar(savedVars, "GLOBAL_LLM_CACHE_TTL_MS", GLOBAL, "3600000");
        expectVar(savedVars, "GLOBAL_LLM_CACHE_REGENERATE", GLOBAL, "false");
        expectVar(savedVars, "GLOBAL_EMAIL_PROVIDER", GLOBAL, "BREVO");
        expectVar(savedVars, "GLOBAL_EMAIL_API_KEY", GLOBAL, "");
        expectVar(savedVars, "GLOBAL_SENDER_EMAIL", GLOBAL, "");
        expectVar(savedVars, "GLOBAL_SENDER_NAME", GLOBAL, "");
        expectVar(savedVars, "GLOBAL_RECIPIENT_EMAIL", GLOBAL, "");
        expectVar(savedVars, "GLOBAL_RAG_ENABLED", GLOBAL, "false");
        expectVar(savedVars, "GLOBAL_DEFAULT_EMBEDDING_MODEL", GLOBAL, "");
        expectVar(savedVars, "GLOBAL_DEFAULT_EMBEDDING_STORE", GLOBAL, "");
        expectVar(savedVars, "GLOBAL_DEFAULT_AI_AGENT", GLOBAL, "");
        expectVar(savedVars, "GLOBAL_PII_SLOT_TTL_HOURS", GLOBAL, "24");
    }

    @Test
    void shouldSaveRagAndRerankDefaults() {
        List<TurConfigVar> savedVars = savedDefaults();
        // T328 — SN RAG relevance gate + reranker defaults.
        expectVar(savedVars, "GLOBAL_RAG_SN_SIMILARITY_THRESHOLD", GLOBAL, "0.0");
        expectVar(savedVars, "GLOBAL_RAG_SN_RERANK_ENABLED", GLOBAL, "false");
        expectVar(savedVars, "GLOBAL_RAG_SN_RERANK_TOP_N", GLOBAL, "20");
        // T331 — grounded follow-up suggestions (default off).
        expectVar(savedVars, "GLOBAL_RAG_SN_FOLLOWUPS_ENABLED", GLOBAL, "false");
        // T330 — groundedness audit (default off).
        expectVar(savedVars, "GLOBAL_RAG_SN_GROUNDEDNESS_CHECK_ENABLED", GLOBAL, "false");
        // T337 — pluggable reranker strategy (default LLM) + connection vars.
        expectVar(savedVars, "GLOBAL_RAG_SN_RERANK_STRATEGY", GLOBAL, "LLM");
        expectVar(savedVars, "GLOBAL_RAG_SN_RERANK_ENDPOINT", GLOBAL, "");
        expectVar(savedVars, "GLOBAL_RAG_SN_RERANK_MODEL", GLOBAL, "");
        expectVar(savedVars, "GLOBAL_RAG_SN_RERANK_API_KEY", GLOBAL, "");
    }

    @Test
    void shouldNotCreateConfigVarWhenAlreadyExists() {
        // Mock returns a fresh TurConfigVar (blank value) for every row.
        // ensureConfigVar leaves these alone (existing row → no save), but
        // ensureSecretConfigVar AUTO-HEALS blank values for the HMAC
        // secret. So save is called exactly once — for the secret heal —
        // and the other 49 rows are skipped.
        when(turConfigVarRepository.findById(anyString()))
                .thenReturn(Optional.of(new TurConfigVar()));

        turConfigVarOnStartup.createDefaultRows();

        ArgumentCaptor<TurConfigVar> captor = ArgumentCaptor.forClass(TurConfigVar.class);
        verify(turConfigVarRepository, times(1)).save(captor.capture());
        TurConfigVar saved = captor.getValue();
        assertTrue("GLOBAL_CODE_INTERPRETER_URL_SIGNING_SECRET".equals(saved.getId())
                || saved.getValue() != null && !saved.getValue().isBlank(),
                "Only the URL signing secret should be auto-healed; got id=" + saved.getId()
                        + " value-length=" + (saved.getValue() == null ? -1 : saved.getValue().length()));
    }
}
