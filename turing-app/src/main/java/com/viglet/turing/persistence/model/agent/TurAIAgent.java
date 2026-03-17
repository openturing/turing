package com.viglet.turing.persistence.model.agent;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
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

    @Column(nullable = true, length = 255)
    private String description;

    @Column(length = 500)
    private String avatarUrl;

    @Lob
    @Column(name = "system_prompt")
    private String systemPrompt;

    @Column(nullable = false)
    private int enabled;

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
}
