package com.viglet.turing.persistence.model.llm;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.viglet.core.jpa.VigletAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "llm_token_usage")
public class TurLLMTokenUsage implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @VigletAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @ManyToOne
    @JoinColumn(name = "llm_instance_id", nullable = false)
    private TurLLMInstance turLLMInstance;

    @Column(name = "vendor_id", nullable = false, length = 20)
    private String vendorId;

    @Column(name = "model_name", length = 100)
    private String modelName;

    @Column(length = 255)
    private String username;

    @Column(name = "input_tokens", nullable = false)
    private long inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private long outputTokens;

    @Column(name = "total_tokens", nullable = false)
    private long totalTokens;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * T276 / §XIV.5.2 — owning tenant for cost attribution. Plain column (not
     * {@code @TenantId}) so platform billing can roll usage up <em>across</em>
     * tenants with a {@code GROUP BY tenantId}; stamped from the current tenant
     * on write. {@code DEFAULT} for single-tenant installs.
     */
    @Column(name = "tenantId", length = 40)
    private String tenantId;

    /**
     * T289 / §XVI.1 — id of the {@link com.viglet.turing.persistence.model.agent.TurAIAgent}
     * this turn belongs to, when the call originated from the agent chat
     * executor. {@code null} for non-agent calls (direct LLM chat API, weekly
     * report, memory compression) so the cost dashboard can group "per agent"
     * without those rows polluting an agent's bill.
     */
    @Column(name = "agent_id", length = 255)
    private String agentId;

    /**
     * T289 / §XVI.1 — coarse cost-attribution stage (e.g. {@code chat.live},
     * {@code chat.background}, {@code chat.skill}). Reuses the {@code STAGE_CHAT_*}
     * spirit from T32 so live vs. background spend splits for free. Stored as a
     * short string; {@code null} on legacy / un-tagged rows.
     */
    @Column(name = "stage", length = 40)
    private String stage;

    /**
     * T289 / §XVI.1 — frozen USD cost of this turn, computed at write time as
     * {@code (inputTokens × inputRate + outputTokens × outputRate)} from the
     * {@code tur_llm_price} table. Frozen-at-capture (standard billing) so a
     * later price edit doesn't silently rewrite history. {@code 0.0} for local /
     * embedded / unpriced models — the "$0 fully local" story keys off this.
     */
    @Column(name = "cost_usd", nullable = false)
    private double costUsd;

    /**
     * T742 / §XLIX — id of the {@link com.viglet.turing.persistence.model.gateway.TurGatewayKey}
     * virtual key this turn was billed to, when the call arrived through the
     * Governed LLM Gateway ({@code /v1/*}). {@code null} for every non-gateway
     * call, so per-key spend groups cleanly ({@code GROUP BY keyId}) without
     * polluting agent/tenant rollups.
     */
    @Column(name = "key_id", length = 40)
    private String keyId;
}
