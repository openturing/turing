/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.onstartup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * T986 / §XCV — the boot failure, made to say its name.
 *
 * <p>Two things are asserted and they fail for different reasons. That the refusal
 * happens at all, naming <b>both</b> settings that contradict each other — a message
 * naming only the missing key sends the operator to fix the half they chose on
 * purpose. And that the reason reaches <b>stderr</b>, which is the whole point:
 * this failure's original symptom was exit 1 with a log ending mid-sentence, and a
 * test that only asserts the exception would have passed against that.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurStartupConfigurationCheckTest {

    private static final String STRONG_KEY = "wnyV0oXRLOsBSJNL7TshaqY65MEv3aTMn6SSKiz/SZk=";

    /** The pair that cannot work: a key to encrypt, and nothing to encrypt it with. */
    @Test
    void aProviderKeyWithNoCryptoKeyIsRefusedByName() {
        assertThatThrownBy(() -> check(production(), "").refuseConfigurationStartupCannotFinish())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OPENAI_API_KEY")
                .hasMessageContaining("turing.ai.crypto.key")
                .hasMessageContaining("turing.startup.default-llm.enabled");
    }

    /**
     * The assertion the original symptom would have defeated. The exception alone
     * proved nothing: it was thrown before too, and the operator never saw it.
     */
    @Test
    void theReasonReachesStderrEvenWhenNothingIsLogging() {
        PrintStream original = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            assertThatThrownBy(() -> check(production(), "").refuseConfigurationStartupCannotFinish())
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            System.setErr(original);
        }

        String stderr = captured.toString(StandardCharsets.UTF_8);
        assertThat(stderr)
                .contains("Viglet Turing ES cannot start")
                .contains("TURING_AI_CRYPTO_KEY")
                .contains("OPENAI_API_KEY");
    }

    /** A sample value is refused for its own reason, not mistaken for absence. */
    @Test
    void aSampleCryptoKeyIsRefusedAsASampleAndNotAsMissing() {
        assertThatThrownBy(() -> check(production(), "sample-key-for-crypto")
                .refuseConfigurationStartupCannotFinish())
                .hasMessageContaining("sample/dev value")
                .hasMessageContaining("crypto-key-rotation.md");
    }

    /** Configured properly, the check is silent — it adds no rule of its own. */
    @Test
    void aConfiguredKeyPasses() {
        assertThatCode(() -> check(production(), STRONG_KEY).refuseConfigurationStartupCannotFinish())
                .doesNotThrowAnyException();
    }

    /**
     * Nothing to provision, nothing to encrypt. A production install with no
     * provider key runs fine without a crypto key today, and must keep doing so.
     */
    @Test
    void noProviderKeyMeansNothingToRefuse() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        assertThatCode(() -> new TurStartupConfigurationCheck(environment, "")
                .refuseConfigurationStartupCannotFinish()).doesNotThrowAnyException();
    }

    /** Provisioning turned off is the escape hatch the message offers, so it must work. */
    @Test
    void disabledProvisioningIsNotRefused() {
        MockEnvironment environment = production();
        environment.setProperty("turing.startup.default-llm.enabled", "false");
        assertThatCode(() -> new TurStartupConfigurationCheck(environment, "")
                .refuseConfigurationStartupCannotFinish()).doesNotThrowAnyException();
    }

    /** Outside production the dev fallback key is legitimate, so the pair is fine. */
    @Test
    void outsideProductionTheDevFallbackIsAllowedToStand() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("OPENAI_API_KEY", "sk-test");
        assertThatCode(() -> new TurStartupConfigurationCheck(environment, "")
                .refuseConfigurationStartupCannotFinish()).doesNotThrowAnyException();
    }

    /** Gemini is the other key the provisioner reads, so it is refused the same way. */
    @Test
    void theGeminiKeyIsCheckedToo() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        environment.setProperty("GEMINI_API_KEY", "gm-test");
        assertThatThrownBy(() -> new TurStartupConfigurationCheck(environment, "")
                .refuseConfigurationStartupCannotFinish())
                .hasMessageContaining("GEMINI_API_KEY");
    }

    /**
     * The message is read off a console, and a Windows console rendered the em dash
     * this text first shipped with as a replacement character — in the middle of the
     * one sentence explaining what to do. Anything outside ASCII is a bug here.
     */
    @Test
    void theMessageIsPlainAsciiBecauseAConsoleHasToRenderIt() {
        String message = assertThatThrownBy(
                () -> check(production(), "").refuseConfigurationStartupCannotFinish())
                .actual().getMessage();

        assertThat(message).matches("(?s)\\A\\p{ASCII}*\\z");
    }

    private static MockEnvironment production() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("production");
        environment.setProperty("OPENAI_API_KEY", "sk-test");
        return environment;
    }

    private static TurStartupConfigurationCheck check(MockEnvironment environment, String cryptoKey) {
        return new TurStartupConfigurationCheck(environment, cryptoKey);
    }
}
