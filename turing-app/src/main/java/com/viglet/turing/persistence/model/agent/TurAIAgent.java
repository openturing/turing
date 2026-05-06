package com.viglet.turing.persistence.model.agent;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import com.viglet.turing.persistence.model.customtool.TurCustomTool;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.model.persona.TurPersona;
import com.viglet.turing.persistence.model.store.TurStoreInstance;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "ai_agent")
public class TurAIAgent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = true, length = 500)
    private String description;

    @Column(length = 150)
    private String icon;

    @Lob
    @Column(name = "system_prompt")
    private String systemPrompt;

    @Lob
    @Column(name = "systemPromptMetaPrompt")
    private String systemPromptMetaPrompt;

    @Column(nullable = false)
    private int enabled;

    @Column(name = "rag_enabled", nullable = false)
    private boolean ragEnabled = false;

    /**
     * When {@code true}, the agent enqueues every chat turn into the chat
     * memory pipeline so it can be persisted by {@code turing.logging.engine}
     * (mongodb / redis) keyed by {@code conversationId}. Has no effect when
     * the logging engine is set to {@code none}.
     *
     * @since 2026.2.6
     */
    @Column(name = "chat_memory_enabled", nullable = false)
    private boolean chatMemoryEnabled = false;

    /**
     * How often the in-memory chat-memory queue is flushed to the configured
     * logging engine. The worker buffers turns and writes them in batches
     * every {@code N} minutes so high-frequency conversations don't generate
     * one I/O round-trip per token. Default: 5 minutes.
     *
     * @since 2026.2.6
     */
    @Column(name = "chat_memory_flush_interval_minutes", nullable = false)
    private int chatMemoryFlushIntervalMinutes = 5;

    /**
     * Hard cap on how many messages are kept in the persisted chat memory
     * document for a single conversation — older turns are dropped via
     * {@code $slice} (Mongo) or list trimming (Redis). Prevents unbounded
     * growth on long-running conversations. Default: 100.
     *
     * @since 2026.2.6
     */
    @Column(name = "chat_memory_max_messages", nullable = false)
    private int chatMemoryMaxMessages = 100;

    @Column(length = 2000)
    private String nativeTools;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "ai_agent_llm_instance",
            joinColumns = @JoinColumn(name = "agent_id"),
            inverseJoinColumns = @JoinColumn(name = "llm_instance_id"))
    private Set<TurLLMInstance> llmInstances = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "ai_agent_mcp_server",
            joinColumns = @JoinColumn(name = "agent_id"),
            inverseJoinColumns = @JoinColumn(name = "mcp_server_id"))
    private Set<TurMcpServer> mcpServers = new HashSet<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "ai_agent_custom_tool",
            joinColumns = @JoinColumn(name = "agent_id"),
            inverseJoinColumns = @JoinColumn(name = "custom_tool_id"))
    private Set<TurCustomTool> customTools = new HashSet<>();

    @ManyToOne
    @JoinColumn(name = "embedding_model_instance_id")
    private TurEmbeddingModel turEmbeddingModelInstance;

    @ManyToOne
    @JoinColumn(name = "store_instance_id")
    private TurStoreInstance turStoreInstance;

    /**
     * Optional voice profile applied at chat time. When set, the
     * persona's system instruction, style guidelines, vocabulary
     * constraints, brand context, and few-shot examples are fused into
     * the system prompt before the LLM is called, and the persona's
     * forbidden-term validator runs over the response.
     *
     * @since 2026.2.6
     */
    @ManyToOne
    @JoinColumn(name = "persona_id")
    private TurPersona persona;
}
