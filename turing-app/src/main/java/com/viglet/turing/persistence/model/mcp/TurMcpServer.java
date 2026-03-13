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

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = true, length = 255)
    private String description;

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
