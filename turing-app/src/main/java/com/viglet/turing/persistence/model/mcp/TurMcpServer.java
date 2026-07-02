package com.viglet.turing.persistence.model.mcp;

import java.io.Serial;
import java.io.Serializable;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;
import com.viglet.core.tenancy.VigletTenantOwnedInfra;

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
public class TurMcpServer implements Serializable, VigletTenantOwnedInfra {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
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

    /**
     * T294 — wire transport for {@code HTTP} servers (SSE vs Streamable HTTP).
     * Nullable: a {@code null} value (legacy rows, {@code COMMAND} servers)
     * is treated as {@link TurMcpServerTransportType#SSE} at runtime, so the
     * column is purely opt-in and existing servers are unaffected.
     *
     * @since 2026.3.4
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "transport_type", length = 20)
    private TurMcpServerTransportType transportType;

    @Column(nullable = false)
    private int enabled;
}
