package com.viglet.turing.persistence.model.mcp;

import java.io.Serial;
import java.io.Serializable;

import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "mcp_server")
public class TurMcpServer implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    /**
     * T275 / §XIV.5.1 — owning tenant for BYO infra. A non-null value is a
     * tenant's own instance (their key/endpoint); {@code null} is a
     * platform-provided GLOBAL instance every tenant may use. Deliberately
     * NOT {@code @TenantId} — that would filter the shared NULLs out; the
     * repositories use an explicit {@code tenantId = :current OR IS NULL}.
     */
    @jakarta.persistence.Column(name = "tenantId", length = 40)
    private String tenantId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = true, length = 500)
    private String description;

    /**
     * LLM-facing guidance injected into the agent system prompt when this MCP
     * server is attached to the chatting agent (and enabled). Tells the model
     * how and when to use the tools this server exposes. Free text — stored as
     * {@code longtext}, mapped literally to the {@code llmInstructions} column.
     *
     * @since 2026.3.1
     */
    @Column(columnDefinition = "longtext")
    private String llmInstructions;

    /**
     * Companion brief for the "Help me write" authoring affordance on the
     * {@link #llmInstructions} field. UI-only state — persisted so the brief
     * survives reloads; never read at runtime.
     *
     * @since 2026.3.1
     */
    @Column(columnDefinition = "longtext")
    private String llmInstructionsMetaPrompt;

    @Column(length = 150)
    private String icon;

    @Column
    private String url;

    @Column
    private String command;

    @Column
    private String args;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TurMcpServerType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_type", nullable = false, length = 10)
    private TurMcpServerConnectionType connectionType;

    @Column(nullable = false)
    private int enabled;
}
