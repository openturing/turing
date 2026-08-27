package com.viglet.turing.persistence.dto.mcp;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.viglet.turing.persistence.model.mcp.TurMcpServer;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TurMcpServerDto extends TurMcpServer {

    /**
     * T1032 / §D.12 — whether a bearer token is stored, without disclosing it.
     * The admin form needs to distinguish "no credential configured" from "one is
     * set, leave the field blank to keep it"; the token itself is
     * {@code @JsonIgnore}d on the entity and must stay that way.
     *
     * <p>Derived, not stored: there is no setter, so a request body cannot claim
     * a credential exists.
     *
     * @since 2026.3.4
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public boolean isAuthTokenSet() {
        return getAuthTokenEncrypted() != null && !getAuthTokenEncrypted().isBlank();
    }
}
