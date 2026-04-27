package com.viglet.turing.exchange.sn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Exchange DTO for an SN site's GenAI binding. Since 2026.2.4 the binding
 * carries only a reference to an AI agent — RAG/LLM/embedding/store now live
 * on the agent. Older exports may still contain the dropped fields; they are
 * silently ignored thanks to {@link JsonIgnoreProperties}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Setter
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TurSNSiteGenAiExchange {
    private String id;
    private String sitePrompt;
    /** ID of the referenced AI agent. Resolved on import. */
    private String turAIAgent;
}
