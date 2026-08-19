/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.exception;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * T973 / §LXXXII — the quota refusal as the console actually receives it.
 *
 * <p>T959 built a body and the console parses one, and until now nothing had
 * ever read the thing in between. The four tests that mention 402 or 429 all
 * stop at the service; the agent-create test cannot go further because
 * {@code standaloneSetup} registers no advice, so the exception escapes the
 * dispatcher instead of becoming a response.
 *
 * <p>That leaves the contract unheld at its most fragile point. The console's
 * {@code parseQuotaRefusal} returns null for any body whose {@code type} is not
 * {@code tag:viglet.com,2026:errors/quota-exhausted} — deliberately, so a
 * provider's own 429 is not dressed up as an exhausted plan — which means a
 * drift in that one string does not break a build. It silently stops the banner
 * from rendering and hands the operator back the bare status T959 existed to
 * replace.
 *
 * <p>So the literals here are copied from the frontend on purpose: the property
 * names are read by {@code src/lib/quota-refusal.ts}, and if one of them has to
 * change it changes on both sides in the same commit.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurQuotaRefusalResponseTest {

    /** What {@code QUOTA_EXHAUSTED_TYPE} spells in {@code quota-refusal.ts}. */
    private static final String CONSOLE_TYPE = "tag:viglet.com,2026:errors/quota-exhausted";

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RefusingController())
                .setControllerAdvice(new TurGlobalExceptionHandler())
                .build();
    }

    /**
     * The monthly token budget: 429, a reset instant, and a signed upgrade
     * target. Every field the banner renders is asserted by the name the
     * frontend reads it under.
     */
    @Test
    void theTokenBudgetRefusalCarriesEveryFieldTheBannerReads() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/probe/tokens"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.type").value(CONSOLE_TYPE))
                .andExpect(jsonPath("$.resource").value("monthlyTokens"))
                .andExpect(jsonPath("$.used").value(100000))
                .andExpect(jsonPath("$.limit").value(100000))
                .andExpect(jsonPath("$.resetsAt").value("2026-09-01T00:00Z"))
                .andExpect(jsonPath("$.upgradeUrl")
                        .value("https://viglet.cloud/upgrade?install=i&sig=abc"))
                .andExpect(jsonPath("$.detail").exists());
    }

    /** A resource count: 402, and no reset — waiting does not free an agent. */
    @Test
    void theAgentLimitRefusalIs402AndNamesNoReset() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/probe/agents"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.type").value(CONSOLE_TYPE))
                .andExpect(jsonPath("$.resource").value("agents"))
                .andExpect(jsonPath("$.used").value(3))
                .andExpect(jsonPath("$.limit").value(3))
                .andExpect(jsonPath("$.resetsAt").doesNotExist());
    }

    /**
     * An install that can sign no upgrade target omits the property rather than
     * sending a blank one, so the console can tell "no paid exit here" from "a
     * link that goes nowhere" — which is what its {@code upgradeUrl === null}
     * branch decides on.
     */
    @Test
    void anUnsignableUpgradeTargetIsAbsentRatherThanBlank() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/probe/unsigned"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.upgradeUrl").doesNotExist());
    }

    /** The three endpoints the advice is asked to render a refusal for. */
    @RestController
    static class RefusingController {

        @GetMapping("/probe/tokens")
        public String tokens() {
            throw new TurQuotaExhaustedException(HttpStatus.TOO_MANY_REQUESTS, "monthlyTokens",
                    100_000L, 100_000L,
                    OffsetDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                    "https://viglet.cloud/upgrade?install=i&sig=abc",
                    "Plan limit reached for 'monthlyTokens': 100000 of 100000 used.");
        }

        @GetMapping("/probe/agents")
        public String agents() {
            throw new TurQuotaExhaustedException(HttpStatus.PAYMENT_REQUIRED, "agents", 3L, 3L,
                    null, "https://viglet.cloud/upgrade?install=i&sig=abc",
                    "Plan limit reached for 'agents': 3 of 3 used.");
        }

        @GetMapping("/probe/unsigned")
        public String unsigned() {
            throw new TurQuotaExhaustedException(HttpStatus.PAYMENT_REQUIRED, "sites", 2L, 2L,
                    null, null, "Plan limit reached for 'sites': 2 of 2 used.");
        }
    }
}
