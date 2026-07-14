package com.viglet.turing.api.system;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.email.TurEmailService;
import com.viglet.turing.genai.tool.TurCodeInterpreterToolService;
import com.viglet.turing.genai.tool.TurCodeInterpreterToolService.TurDockerStatus;
import com.viglet.turing.genai.transcription.TurAudioChunker;
import com.viglet.turing.genai.transcription.TurAudioChunker.TurFfmpegStatus;
import com.viglet.turing.genai.urlfetch.TurUrlFetchService;
import com.viglet.turing.genai.urlfetch.TurUrlFetchService.TurBrowserlessStatus;
import com.viglet.turing.system.TurGlobalSettingsService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/system/global-settings")
@Tag(name = "Global Settings", description = "Global Settings API")
public class TurGlobalSettingsAPI {
    private final TurGlobalSettingsService turGlobalSettingsService;
    private final TurEmailService turEmailService;
    private final TurCodeInterpreterToolService turCodeInterpreterToolService;
    private final TurAudioChunker turAudioChunker;
    private final TurUrlFetchService turUrlFetchService;

    public TurGlobalSettingsAPI(TurGlobalSettingsService turGlobalSettingsService,
            TurEmailService turEmailService,
            TurCodeInterpreterToolService turCodeInterpreterToolService,
            TurAudioChunker turAudioChunker,
            TurUrlFetchService turUrlFetchService) {
        this.turGlobalSettingsService = turGlobalSettingsService;
        this.turEmailService = turEmailService;
        this.turCodeInterpreterToolService = turCodeInterpreterToolService;
        this.turAudioChunker = turAudioChunker;
        this.turUrlFetchService = turUrlFetchService;
    }

    @Operation(summary = "Get global settings")
    @GetMapping
    public TurGlobalSettingsBean getGlobalSettings() {
        return TurGlobalSettingsBean.builder()
                .decimalSeparator(turGlobalSettingsService.getDecimalSeparator())
                .pythonExecutable(turGlobalSettingsService.getPythonExecutable())
                .pythonRequirements(turGlobalSettingsService.getPythonRequirements())
                .codeInterpreterExecutionMode(turGlobalSettingsService.getCodeInterpreterExecutionMode())
                .codeInterpreterDockerImage(turGlobalSettingsService.getCodeInterpreterDockerImage())
                .codeInterpreterSkillImage(turGlobalSettingsService.getCodeInterpreterSkillImage())
                .defaultLlmId(turGlobalSettingsService.getDefaultLlmId())
                .modelLaneFastId(turGlobalSettingsService
                        .getModelLaneInstanceId(com.viglet.turing.system.lane.TurModelLane.FAST))
                .modelLaneReasoningId(turGlobalSettingsService
                        .getModelLaneInstanceId(com.viglet.turing.system.lane.TurModelLane.REASONING))
                .modelLaneCheapId(turGlobalSettingsService
                        .getModelLaneInstanceId(com.viglet.turing.system.lane.TurModelLane.CHEAP))
                .llmFallbackChainIds(turGlobalSettingsService.getLlmFallbackChainIds())
                .llmFallbackMode(turGlobalSettingsService.getLlmFallbackMode())
                .secondOpinionEnabled(turGlobalSettingsService.isSecondOpinionEnabled())
                .secondOpinionLlmId(turGlobalSettingsService.getSecondOpinionLlmId())
                .llmCacheEnabled(turGlobalSettingsService.isLlmCacheEnabled())
                .llmCacheTtlMs(turGlobalSettingsService.getLlmCacheTtlMs())
                .llmCacheRegenerate(turGlobalSettingsService.isLlmCacheRegenerate())
                .emailProvider(turGlobalSettingsService.getEmailProvider())
                .emailApiKey(turGlobalSettingsService.getEmailApiKey())
                .senderEmail(turGlobalSettingsService.getSenderEmail())
                .senderName(turGlobalSettingsService.getSenderName())
                .recipientEmail(turGlobalSettingsService.getRecipientEmail())
                .ragEnabled(turGlobalSettingsService.isRagEnabled())
                .defaultEmbeddingModelId(turGlobalSettingsService.getDefaultEmbeddingModelId())
                .defaultEmbeddingStoreId(turGlobalSettingsService.getDefaultEmbeddingStoreId())
                .defaultAiAgentId(turGlobalSettingsService.getDefaultAiAgentId())
                .piiSlotTtlHours(turGlobalSettingsService.getPiiSlotTtlHours())
                .ragSnRerankEnabled(turGlobalSettingsService.isRagSnRerankEnabled())
                .ragSnRerankTopN(turGlobalSettingsService.getRagSnRerankTopN())
                .ragSnRerankStrategy(turGlobalSettingsService.getRagSnRerankStrategy())
                .ragSnRerankEndpoint(turGlobalSettingsService.getRagSnRerankEndpoint())
                .ragSnRerankModel(turGlobalSettingsService.getRagSnRerankModel())
                .ragSnRerankRegion(turGlobalSettingsService.getRagSnRerankRegion())
                .ragSnRerankVertexProject(turGlobalSettingsService.getRagSnRerankVertexProject())
                .ragSnRerankVertexLocation(turGlobalSettingsService.getRagSnRerankVertexLocation())
                // write-only secret: never echo the key, only its set/not-set status
                .ragSnRerankApiKey("")
                .ragSnRerankApiKeySet(turGlobalSettingsService.isRagSnRerankApiKeySet())
                .ragSnRerankCacheEnabled(turGlobalSettingsService.isRagSnRerankCacheEnabled())
                .transcriptionStrategy(turGlobalSettingsService.getTranscriptionStrategy())
                .transcriptionEndpoint(turGlobalSettingsService.getTranscriptionEndpoint())
                .transcriptionModel(turGlobalSettingsService.getTranscriptionModel())
                .transcriptionMaxUploadBytes(turGlobalSettingsService.getTranscriptionMaxUploadBytes())
                // write-only secret: never echo the key, only its set/not-set status
                .transcriptionApiKey("")
                .transcriptionApiKeySet(turGlobalSettingsService.isTranscriptionApiKeySet())
                .urlFetchMode(turGlobalSettingsService.getUrlFetchMode())
                .urlFetchBrowserlessUrl(turGlobalSettingsService.getUrlFetchBrowserlessUrl())
                // write-only secret: never echo the token, only its set/not-set status
                .urlFetchBrowserlessToken("")
                .urlFetchBrowserlessTokenSet(turGlobalSettingsService.isUrlFetchBrowserlessTokenSet())
                .build();
    }

    @Operation(summary = "Update global settings")
    @PutMapping
    public TurGlobalSettingsBean updateGlobalSettings(@RequestBody TurGlobalSettingsBean globalSettings) {
        return TurGlobalSettingsBean.builder()
                .decimalSeparator(turGlobalSettingsService
                        .updateDecimalSeparator(globalSettings.getDecimalSeparator()))
                .pythonExecutable(turGlobalSettingsService
                        .updatePythonExecutable(globalSettings.getPythonExecutable()))
                .pythonRequirements(turGlobalSettingsService
                        .updatePythonRequirements(globalSettings.getPythonRequirements()))
                .codeInterpreterExecutionMode(turGlobalSettingsService
                        .updateCodeInterpreterExecutionMode(globalSettings.getCodeInterpreterExecutionMode()))
                .codeInterpreterDockerImage(turGlobalSettingsService
                        .updateCodeInterpreterDockerImage(globalSettings.getCodeInterpreterDockerImage()))
                .codeInterpreterSkillImage(turGlobalSettingsService
                        .updateCodeInterpreterSkillImage(globalSettings.getCodeInterpreterSkillImage()))
                .defaultLlmId(turGlobalSettingsService
                        .updateDefaultLlmId(globalSettings.getDefaultLlmId()))
                .modelLaneFastId(turGlobalSettingsService.updateModelLaneInstanceId(
                        com.viglet.turing.system.lane.TurModelLane.FAST,
                        globalSettings.getModelLaneFastId()))
                .modelLaneReasoningId(turGlobalSettingsService.updateModelLaneInstanceId(
                        com.viglet.turing.system.lane.TurModelLane.REASONING,
                        globalSettings.getModelLaneReasoningId()))
                .modelLaneCheapId(turGlobalSettingsService.updateModelLaneInstanceId(
                        com.viglet.turing.system.lane.TurModelLane.CHEAP,
                        globalSettings.getModelLaneCheapId()))
                .llmFallbackChainIds(turGlobalSettingsService
                        .updateLlmFallbackChainIds(globalSettings.getLlmFallbackChainIds()))
                .llmFallbackMode(turGlobalSettingsService
                        .updateLlmFallbackMode(globalSettings.getLlmFallbackMode()))
                .secondOpinionEnabled(turGlobalSettingsService
                        .updateSecondOpinionEnabled(globalSettings.isSecondOpinionEnabled()))
                .secondOpinionLlmId(turGlobalSettingsService
                        .updateSecondOpinionLlmId(globalSettings.getSecondOpinionLlmId()))
                .llmCacheEnabled(turGlobalSettingsService
                        .updateLlmCacheEnabled(globalSettings.isLlmCacheEnabled()))
                .llmCacheTtlMs(turGlobalSettingsService
                        .updateLlmCacheTtlMs(globalSettings.getLlmCacheTtlMs()))
                .llmCacheRegenerate(turGlobalSettingsService
                        .updateLlmCacheRegenerate(globalSettings.isLlmCacheRegenerate()))
                .emailProvider(turGlobalSettingsService
                        .updateEmailProvider(globalSettings.getEmailProvider()))
                .emailApiKey(turGlobalSettingsService
                        .updateEmailApiKey(globalSettings.getEmailApiKey()))
                .senderEmail(turGlobalSettingsService
                        .updateSenderEmail(globalSettings.getSenderEmail()))
                .senderName(turGlobalSettingsService
                        .updateSenderName(globalSettings.getSenderName()))
                .recipientEmail(turGlobalSettingsService
                        .updateRecipientEmail(globalSettings.getRecipientEmail()))
                .ragEnabled(turGlobalSettingsService
                        .updateRagEnabled(globalSettings.isRagEnabled()))
                .defaultEmbeddingModelId(turGlobalSettingsService
                        .updateDefaultEmbeddingModelId(globalSettings.getDefaultEmbeddingModelId()))
                .defaultEmbeddingStoreId(turGlobalSettingsService
                        .updateDefaultEmbeddingStoreId(globalSettings.getDefaultEmbeddingStoreId()))
                .defaultAiAgentId(turGlobalSettingsService
                        .updateDefaultAiAgentId(globalSettings.getDefaultAiAgentId()))
                .piiSlotTtlHours(turGlobalSettingsService
                        .updatePiiSlotTtlHours(globalSettings.getPiiSlotTtlHours()))
                .ragSnRerankEnabled(turGlobalSettingsService
                        .updateRagSnRerankEnabled(globalSettings.isRagSnRerankEnabled()))
                .ragSnRerankTopN(turGlobalSettingsService
                        .updateRagSnRerankTopN(globalSettings.getRagSnRerankTopN()))
                .ragSnRerankStrategy(turGlobalSettingsService
                        .updateRagSnRerankStrategy(globalSettings.getRagSnRerankStrategy()))
                .ragSnRerankEndpoint(turGlobalSettingsService
                        .updateRagSnRerankEndpoint(globalSettings.getRagSnRerankEndpoint()))
                .ragSnRerankModel(turGlobalSettingsService
                        .updateRagSnRerankModel(globalSettings.getRagSnRerankModel()))
                .ragSnRerankRegion(turGlobalSettingsService
                        .updateRagSnRerankRegion(globalSettings.getRagSnRerankRegion()))
                .ragSnRerankVertexProject(turGlobalSettingsService
                        .updateRagSnRerankVertexProject(globalSettings.getRagSnRerankVertexProject()))
                .ragSnRerankVertexLocation(turGlobalSettingsService
                        .updateRagSnRerankVertexLocation(globalSettings.getRagSnRerankVertexLocation()))
                // Write-only secret — persisted only when a non-blank value
                // arrives, blank leaves the stored key untouched, never echoed
                .ragSnRerankApiKey("")
                .ragSnRerankApiKeySet(updateRerankApiKeyIfPresent(globalSettings.getRagSnRerankApiKey()))
                .ragSnRerankCacheEnabled(turGlobalSettingsService
                        .updateRagSnRerankCacheEnabled(globalSettings.isRagSnRerankCacheEnabled()))
                .transcriptionStrategy(turGlobalSettingsService
                        .updateTranscriptionStrategy(globalSettings.getTranscriptionStrategy()))
                .transcriptionEndpoint(turGlobalSettingsService
                        .updateTranscriptionEndpoint(globalSettings.getTranscriptionEndpoint()))
                .transcriptionModel(turGlobalSettingsService
                        .updateTranscriptionModel(globalSettings.getTranscriptionModel()))
                .transcriptionMaxUploadBytes(turGlobalSettingsService
                        .updateTranscriptionMaxUploadBytes(globalSettings.getTranscriptionMaxUploadBytes()))
                // Write-only secret — persisted only when a non-blank value
                // arrives, blank leaves the stored key untouched, never echoed
                .transcriptionApiKey("")
                .transcriptionApiKeySet(updateTranscriptionApiKeyIfPresent(globalSettings.getTranscriptionApiKey()))
                .urlFetchMode(turGlobalSettingsService
                        .updateUrlFetchMode(globalSettings.getUrlFetchMode()))
                .urlFetchBrowserlessUrl(turGlobalSettingsService
                        .updateUrlFetchBrowserlessUrl(globalSettings.getUrlFetchBrowserlessUrl()))
                // Write-only secret — persisted only when a non-blank value
                // arrives, blank leaves the stored token untouched, never echoed
                .urlFetchBrowserlessToken("")
                .urlFetchBrowserlessTokenSet(
                        updateUrlFetchTokenIfPresent(globalSettings.getUrlFetchBrowserlessToken()))
                .build();
    }

    /**
     * T739 — persists the browserless token only when the client sent a non-blank
     * value (write-only field); a blank submit preserves the existing token.
     * Returns the resulting configured status.
     */
    private boolean updateUrlFetchTokenIfPresent(String token) {
        if (token == null || token.isBlank()) {
            return turGlobalSettingsService.isUrlFetchBrowserlessTokenSet();
        }
        return turGlobalSettingsService.updateUrlFetchBrowserlessToken(token);
    }

    /**
     * Persists the Cohere rerank API key only when the client sent a non-blank
     * value (write-only field); a blank submit preserves the existing key.
     * Returns the resulting configured status.
     */
    private boolean updateRerankApiKeyIfPresent(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return turGlobalSettingsService.isRagSnRerankApiKeySet();
        }
        return turGlobalSettingsService.updateRagSnRerankApiKey(apiKey);
    }

    /**
     * T687 — persists the transcription API key only when the client sent a
     * non-blank value (write-only field); a blank submit preserves the existing
     * key. Returns the resulting configured status.
     */
    private boolean updateTranscriptionApiKeyIfPresent(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return turGlobalSettingsService.isTranscriptionApiKeySet();
        }
        return turGlobalSettingsService.updateTranscriptionApiKey(apiKey);
    }

    /**
     * Status + rotation surface for the HMAC URL-signing secret. The
     * actual secret VALUE is never sent to the client — only a boolean
     * "set/not set" status (for GET) and a preview (first 8 Base64 chars)
     * after rotation (for POST). A screenshot of the admin UI must never
     * leak the live secret.
     */
    @Operation(summary = "Status of the URL-signing secret (set vs blank)")
    @GetMapping("/code-interpreter/url-signing-secret")
    public TurUrlSigningSecretStatus getUrlSigningSecretStatus() {
        return new TurUrlSigningSecretStatus(
                turGlobalSettingsService.isCodeInterpreterUrlSigningSecretSet());
    }

    @Operation(summary = "Mint a fresh URL-signing secret. Invalidates every previously emitted signed URL.")
    @PostMapping("/code-interpreter/url-signing-secret/regenerate")
    public TurUrlSigningSecretRotation regenerateUrlSigningSecret() {
        String preview = turGlobalSettingsService.regenerateCodeInterpreterUrlSigningSecret();
        // T652 / §XXXVII.14 — do NOT log any of the secret material (was logging
        // its first 8 chars). The preview is returned in the response for the
        // admin UI to confirm the rotation, but never written to the log.
        log.info("[GlobalSettings] URL-signing secret rotated by admin");
        return new TurUrlSigningSecretRotation(true, preview);
    }

    /** Status payload — only the boolean is sent, never the value. */
    public record TurUrlSigningSecretStatus(boolean configured) {
    }

    /** Rotation result — preview is the first 8 chars only, for confirmation in the UI. */
    public record TurUrlSigningSecretRotation(boolean rotated, String preview) {
    }

    /**
     * T80 — probes whether Docker is reachable from the running server.
     * Lets the admin validate the host before switching the Code
     * Interpreter execution mode to {@code DOCKER}. Never sends anything
     * sensitive — only an availability flag, the daemon's server version,
     * and an error string when unavailable.
     */
    @Operation(summary = "Check whether Docker is available for the Code Interpreter sandbox")
    @GetMapping("/code-interpreter/docker/status")
    public TurDockerStatus getDockerStatus() {
        return turCodeInterpreterToolService.checkDocker();
    }

    /**
     * T688 — probes whether the {@code ffmpeg} toolchain is reachable from the
     * running server. Chunking audio above a backend's per-request limit requires
     * ffmpeg; small clips do not. Lets the admin validate the host before relying
     * on large-file transcription. Never sends anything sensitive — only an
     * availability flag, the ffmpeg version banner, and an error string when
     * unavailable.
     */
    @Operation(summary = "Check whether ffmpeg is available for large-audio transcription chunking")
    @GetMapping("/transcription/ffmpeg/status")
    public TurFfmpegStatus getFfmpegStatus() {
        return turAudioChunker.checkFfmpeg();
    }

    /**
     * T739 / §XLVIII — probes whether the configured {@code browserless} sidecar
     * is reachable, so the admin can validate it before switching the URL-fetch
     * mode to {@code HEADLESS}/{@code AUTO}. Never sends anything sensitive — only
     * an availability flag, the reported browser version, and an error string.
     */
    @Operation(summary = "Check whether the browserless sidecar is reachable for headless URL fetching")
    @GetMapping("/url-fetch/browserless/status")
    public TurBrowserlessStatus getBrowserlessStatus() {
        return turUrlFetchService.checkBrowserless();
    }

    @Operation(summary = "Send a test email to validate email configuration")
    @PostMapping("/email/test")
    public ResponseEntity<TurEmailTestResponse> sendTestEmail() {
        try {
            turEmailService.sendEmail("Viglet Turing ES - Test Email",
                    "<div style=\"font-family:sans-serif;max-width:480px;margin:0 auto;padding:32px;\">"
                    + "<h2 style=\"color:#6366f1;\">Email Configuration Test</h2>"
                    + "<p>This is a test email from <strong>Viglet Turing ES</strong>.</p>"
                    + "<p>If you received this message, your email settings are configured correctly.</p>"
                    + "<hr style=\"border:none;border-top:1px solid #e2e8f0;margin:24px 0;\">"
                    + "<p style=\"font-size:12px;color:#94a3b8;\">Sent by Viglet Turing ES &mdash; Enterprise Search Intelligence Platform</p>"
                    + "</div>");
            return ResponseEntity.ok(new TurEmailTestResponse(true, "Test email sent successfully."));
        } catch (Exception e) {
            log.error("Failed to send test email", e);
            return ResponseEntity.internalServerError()
                    .body(new TurEmailTestResponse(false, "Failed to send test email: " + e.getMessage()));
        }
    }

    public record TurEmailTestResponse(boolean success, String message) {
    }
}
