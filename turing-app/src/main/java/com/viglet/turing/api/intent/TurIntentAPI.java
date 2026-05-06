package com.viglet.turing.api.intent;

import java.util.List;

import org.springframework.security.access.annotation.Secured;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import org.springframework.core.ParameterizedTypeReference;

import java.util.Set;

import org.springframework.ai.tool.ToolCallback;

import com.viglet.turing.domain.agent.TurAIAgentRepositoryPort;
import com.viglet.turing.genai.authoring.AiAuthoringRequest;
import com.viglet.turing.genai.authoring.AiAuthoringResponse;
import com.viglet.turing.genai.authoring.TurAiAuthoringService;
import com.viglet.turing.genai.authoring.intent.IntentGeneration;
import com.viglet.turing.genai.tool.TurNativeToolService;
import com.viglet.turing.genai.tool.TurToolCallbackPipeline;
import com.viglet.turing.persistence.dto.intent.TurIntentDto;
import com.viglet.turing.persistence.mapper.intent.TurIntentMapper;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.persistence.model.intent.TurIntent;
import com.viglet.turing.persistence.repository.agent.TurAIAgentRepository;
import com.viglet.turing.persistence.repository.intent.TurIntentRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Agent-scoped REST API for managing Intents and their Actions.
 * Each AI agent owns its own set of intents — there is no longer a global
 * intent listing.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@RestController
@RequestMapping("/api/ai-agent/{agentId}/intent")
@Tag(name = "Intent", description = "AI Agent Intent API")
public class TurIntentAPI {
    private final TurIntentRepository turIntentRepository;
    private final TurIntentMapper turIntentMapper;
    private final TurAIAgentRepository turAIAgentRepository;
    private final TurAIAgentRepositoryPort turAIAgentRepositoryPort;
    private final TurAiAuthoringService aiAuthoringService;
    private final TurNativeToolService nativeToolService;
    private final TurToolCallbackPipeline toolCallbackPipeline;

    public TurIntentAPI(TurIntentRepository turIntentRepository,
                        TurIntentMapper turIntentMapper,
                        TurAIAgentRepository turAIAgentRepository,
                        TurAIAgentRepositoryPort turAIAgentRepositoryPort,
                        TurAiAuthoringService aiAuthoringService,
                        TurNativeToolService nativeToolService,
                        TurToolCallbackPipeline toolCallbackPipeline) {
        this.turIntentRepository = turIntentRepository;
        this.turIntentMapper = turIntentMapper;
        this.turAIAgentRepository = turAIAgentRepository;
        this.turAIAgentRepositoryPort = turAIAgentRepositoryPort;
        this.aiAuthoringService = aiAuthoringService;
        this.nativeToolService = nativeToolService;
        this.toolCallbackPipeline = toolCallbackPipeline;
    }

    /**
     * System prompt for the Intent AI Authoring chat. Frozen at class
     * load — depends only on the Intent schema, not on the agent.
     */
    private static final String INTENT_SYSTEM_PROMPT = """
            You are helping a user author an Intent inside the Turing
            Enterprise Search platform. An Intent groups related quick-action
            prompts that the AI Agent surfaces to the end user as suggestions
            (chips/buttons). Each Intent has:

            - title (string, required) — short label shown to the user
            - description (string, optional) — longer admin-facing explanation
            - icon (string, required) — a valid Iconify identifier in the
              format `<prefix>:<name>` (e.g. `mdi:home`, `tabler:bulb`,
              `ph:graduation-cap`). MUST come from the `search_icons` tool
              — never an emoji, never a free-form name.
            - enabled (integer 0/1) — whether the Intent appears at runtime
            - actions (array, required, 1+ items) — the quick prompts. Each:
                - label (string) — short visible label on the chip
                - prompt (string) — the full prompt text sent to the LLM when clicked

            BEHAVIOR:
            - Each turn, return a conversational `message` (in the user's
              language) AND the FULL updated `state`. The state is REPLACED
              wholesale on the frontend, so always include every field.
            - Respect any manual edits already in `CURRENT FORM STATE` —
              don't revert them unless the user explicitly asks.
            - When generating actions, write 3 to 6 by default, with prompts
              that read naturally as user requests (first person).
            - The `message` field should be short and conversational (1-3
              sentences). Use it to summarize what you changed and ask if
              anything else is needed.
            - Never include id, agentId, or sortOrder fields — those are
              owned by the backend.

            CONSTRAINTS:
            - title <= 100 chars, description <= 500 chars.
            - icon: ALWAYS use the `search_icons` tool to pick a real Iconify
              identifier (format `<prefix>:<name>`, e.g. `mdi:lightning-bolt`,
              `tabler:bulb`, `ph:graduation-cap`). NEVER invent an icon name
              and NEVER use a raw emoji — the frontend renders Iconify only.
              If the user already picked an icon and didn't ask to change it,
              keep it as-is in the state.
            - Default enabled=1 unless the user asks for the intent disabled.
            """;

    /** Load the agent entity — only for write paths that need the JPA-managed reference. */
    private TurAIAgent loadAgent(String agentId) {
        return turAIAgentRepository.findById(agentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Agent not found"));
    }

    /** Validate the agent exists via the port — for read-only paths that don't need the entity. */
    private void requireAgentExists(String agentId) {
        if (turAIAgentRepositoryPort.findById(agentId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI Agent not found");
        }
    }

    @Operation(summary = "Intent List for an Agent")
    @GetMapping
    @Secured({"ROLE_ADMIN", "INTENT_VIEW"})
    public List<TurIntentDto> turIntentList(@PathVariable String agentId) {
        return turIntentMapper
                .toDtoList(turIntentRepository.findByTurAIAgent_IdOrderByTitleAsc(agentId));
    }

    @Operation(summary = "Intent List for an Agent (enabled only, ordered by sortOrder)")
    @GetMapping("/enabled")
    @Secured({"ROLE_ADMIN", "INTENT_VIEW"})
    public List<TurIntentDto> turIntentListEnabled(@PathVariable String agentId) {
        return turIntentMapper
                .toDtoList(turIntentRepository.findByTurAIAgent_IdAndEnabledOrderBySortOrderAsc(agentId, 1));
    }

    @Operation(summary = "Intent structure")
    @GetMapping("/structure")
    @Secured({"ROLE_ADMIN", "INTENT_VIEW"})
    public TurIntentDto turIntentStructure(@PathVariable String agentId) {
        return turIntentMapper.toDto(new TurIntent());
    }

    @Operation(summary = "Show an Intent of an Agent")
    @GetMapping("/{id}")
    @Secured({"ROLE_ADMIN", "INTENT_VIEW"})
    public TurIntentDto turIntentGet(@PathVariable String agentId, @PathVariable String id) {
        return turIntentRepository.findById(id)
                .filter(intent -> intent.getTurAIAgent() != null
                        && agentId.equals(intent.getTurAIAgent().getId()))
                .map(turIntentMapper::toDto)
                .orElse(new TurIntentDto());
    }

    @Operation(summary = "Update an Intent of an Agent")
    @PutMapping("/{id}")
    @Secured({"ROLE_ADMIN", "INTENT_EDIT"})
    public TurIntentDto turIntentUpdate(@PathVariable String agentId,
                                        @PathVariable String id,
                                        @RequestBody TurIntentDto turIntentDto) {
        TurIntent source = turIntentMapper.toEntity(turIntentDto);
        return turIntentRepository.findById(id)
                .filter(intent -> intent.getTurAIAgent() != null
                        && agentId.equals(intent.getTurAIAgent().getId()))
                .map(existing -> {
                    turIntentMapper.updateEntity(source, existing);
                    turIntentRepository.save(existing);
                    return turIntentMapper.toDto(existing);
                }).orElse(new TurIntentDto());
    }

    @Transactional
    @Operation(summary = "Delete an Intent of an Agent")
    @DeleteMapping("/{id}")
    @Secured({"ROLE_ADMIN", "INTENT_DELETE"})
    public boolean turIntentDelete(@PathVariable String agentId, @PathVariable String id) {
        return turIntentRepository.findById(id)
                .filter(intent -> intent.getTurAIAgent() != null
                        && agentId.equals(intent.getTurAIAgent().getId()))
                .map(intent -> {
                    turIntentRepository.delete(id);
                    return true;
                }).orElse(false);
    }

    @Operation(summary = "Create an Intent for an Agent")
    @PostMapping
    @Secured({"ROLE_ADMIN", "INTENT_CREATE"})
    public TurIntentDto turIntentAdd(@PathVariable String agentId,
                                     @RequestBody TurIntentDto turIntentDto) {
        TurAIAgent agent = loadAgent(agentId);
        TurIntent turIntent = turIntentMapper.toEntity(turIntentDto);
        turIntent.setTurAIAgent(agent);
        turIntent.setActions(turIntentDto.getActions());
        turIntentRepository.save(turIntent);
        return turIntentMapper.toDto(turIntent);
    }

    @Operation(summary = "AI Authoring chat for an Intent — returns a conversational reply plus the updated form snapshot")
    @PostMapping("/chat")
    @Secured({"ROLE_ADMIN", "INTENT_CREATE", "INTENT_EDIT"})
    public AiAuthoringResponse<IntentGeneration> turIntentChat(
            @PathVariable String agentId,
            @RequestBody AiAuthoringRequest<IntentGeneration> request) {
        // Verify agent exists (also enforces auth scope at row level later if needed).
        requireAgentExists(agentId);

        // Expose the Iconify search tool so the LLM can pick a real icon
        // identifier instead of inventing one. The pipeline applies the
        // .md description override + logging — never bypass it.
        ToolCallback[] tools = toolCallbackPipeline.decorate(
                nativeToolService.getToolCallbacks(Set.of("search_icons")));

        return aiAuthoringService.chat(
                request,
                INTENT_SYSTEM_PROMPT,
                new ParameterizedTypeReference<AiAuthoringResponse<IntentGeneration>>() {},
                tools);
    }
}
