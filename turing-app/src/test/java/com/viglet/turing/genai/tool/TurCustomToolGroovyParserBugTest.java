/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import groovy.lang.GroovyShell;
import groovy.lang.Script;

/**
 * Pin tests for the Groovy parser behavior around triple-quote heredocs
 * used by the {@code gerar_proposta_pdf} custom tool. The B2B path of
 * that tool kept hitting "Unexpected character: '\''" at the heredoc
 * opener; multiple workarounds (placeholder, helper named-args, hoisted
 * heredoc with {@code @Field}) failed in different ways.
 *
 * <p>This test exercises the exact compilation path the production code
 * uses — {@code GroovyShell.getClassLoader().parseClass(source, name)} —
 * so it captures the same ANTLR4 parser surface that
 * {@link TurCustomToolCallbackService#scriptFor} hits at runtime.
 *
 * <p>Each scenario isolates one variant of the heredoc pattern so we can
 * tell which one the parser accepts and which one it rejects. When a new
 * Groovy version changes behavior here, the failing scenario will flip
 * green/red and tell us exactly what changed.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.X
 */
class TurCustomToolGroovyParserBugTest {

    private static Class<? extends Script> parse(String source) {
        GroovyShell shell = new GroovyShell();
        @SuppressWarnings("unchecked")
        Class<? extends Script> clazz = (Class<? extends Script>)
                shell.getClassLoader().parseClass(source, "ParserBugTest.groovy");
        return clazz;
    }

    @Test
    @DisplayName("baseline: triple-single-quote heredoc at script top-level parses cleanly")
    void heredocAtScriptTopLevel_parses() {
        // This is the B2C pattern that has worked from day one — line 101
        // of gerar-proposta-pdf.groovy. Pinning it so a Groovy upgrade
        // that breaks even this case fires immediately.
        String src = ""
                + "def x = '''\n"
                + "hello world\n"
                + "with single ' quote\n"
                + "with \"double\" quote\n"
                + "'''\n"
                + "return x";
        assertThatCode(() -> parse(src)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("heredoc inside def-method body — minimal content parses (NOT the bug)")
    void heredocInsideMethodBody_minimalContent_parses() {
        // First hypothesis was "heredoc inside a method fails" — turns out
        // a minimal heredoc body inside a def method() {} parses fine. So
        // the production failure can't be reduced to just position-in-method.
        String src = ""
                + "def generatePdf() {\n"
                + "    def pythonScript = '''\n"
                + "import datetime\n"
                + "PRINT_THIS = \"hello\"\n"
                + "'''\n"
                + "    return pythonScript\n"
                + "}\n"
                + "return generatePdf()";
        assertThatCode(() -> parse(src)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("@Field heredoc at script level — minimal content parses (NOT the bug)")
    void heredocWithAtField_minimalContent_parses() {
        // Second hypothesis: @Field def X = '''...''' is the trigger. Also
        // false — minimal body works. The bug must be content-driven.
        String src = ""
                + "import groovy.transform.Field\n"
                + "@Field def B2B_PYTHON_TEMPLATE = '''\n"
                + "import datetime, os, re\n"
                + "P_DATA   = INPUTS[\"proposta\"]\n"
                + "SHARE_URL = INPUTS.get(\"share_url_b2b\") or \"\"\n"
                + "'''\n"
                + "return B2B_PYTHON_TEMPLATE";
        assertThatCode(() -> parse(src)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("FIX hypothesis: replace \\U with \\\\U keeps semantics, parses")
    void fixHypothesis_doubleBackslashU() throws Exception {
        // Within a Groovy triple-single-quote heredoc, \\U is interpreted
        // as: backslash-escape (one literal \\) followed by U. So at
        // runtime the string contains \U (1 backslash + U) — exactly
        // what the Python regex on the other side wants. And the lexer
        // doesn't choke because \\U is a different token sequence than
        // bare \\U.
        String src = ""
                + "def x = '''\n"
                + "r\"^[\\\\U0001F000-\\\\U0001FFFF]\\s*\"\n"
                + "'''\n"
                + "def y = '''\n"
                + "tail\n"
                + "'''\n"
                + "return x";
        try {
            Class<? extends Script> clazz = parse(src);
            Script script = clazz.getDeclaredConstructor().newInstance();
            String runtime = (String) script.run();
            System.out.println("FIX [double-backslash-U]: parses OK; runtime = "
                    + runtime.replace("\n", "\\n"));
            // The Python sandbox reads `\U0001F000-\U0001FFFF` (single
            // backslash + U). Verify that's what we produce.
            assertThat(runtime).contains("\\U0001F000-\\U0001FFFF");
        } catch (MultipleCompilationErrorsException e) {
            System.out.println("FIX [double-backslash-U]: BREAKS — " + e.getMessage());
            throw e;
        }
    }

    @Test
    @DisplayName("backend sanitizer: leaves source without \\U untouched")
    void sanitizer_passesThroughCleanSource() {
        String clean = "def x = '''\nhello world\n'''\nreturn x";
        assertThat(TurCustomToolCallbackService.sanitizeGroovySource(clean)).isEqualTo(clean);
    }

    @Test
    @DisplayName("backend sanitizer: replaces \\U with \\\\U (preserves \\\\U)")
    void sanitizer_dedoublesOnlyBareU() {
        // Bare \U gets escaped, pre-escaped \\U is left alone.
        String src = "a \\U0001F000 b \\\\U c";
        String expected = "a \\\\U0001F000 b \\\\U c";
        assertThat(TurCustomToolCallbackService.sanitizeGroovySource(src)).isEqualTo(expected);
    }

    @Test
    @DisplayName("backend sanitizer: applied to production file makes it parse")
    void sanitizer_endToEndOnProductionFile() throws Exception {
        java.nio.file.Path file = java.nio.file.Paths.get(
                "D:", "Git", "customer-portal",
                "ai-agent", "tools", "gerar-proposta-pdf.groovy");
        if (!java.nio.file.Files.exists(file)) return;
        String raw = java.nio.file.Files.readString(file);
        String sanitized = TurCustomToolCallbackService.sanitizeGroovySource(raw);
        // Sanity: it actually changed something.
        assertThat(sanitized).isNotEqualTo(raw);
        // And now it parses.
        parse(sanitized);
    }

    @Test
    @DisplayName("backend sanitizer: null and empty don't NPE")
    void sanitizer_nullAndEmpty() {
        assertThat(TurCustomToolCallbackService.sanitizeGroovySource(null)).isEmpty();
        assertThat(TurCustomToolCallbackService.sanitizeGroovySource("")).isEmpty();
    }

    @Test
    @DisplayName("pin: raw production file (no sanitizer) breaks at line 477 col 36")
    void productionFile_rawSource_breaksAtKnownLocation() throws Exception {
        // Pins the bug as a known regression: without sanitizeGroovySource(),
        // the production file fails to parse with the exact error message
        // and location. If a future Groovy upgrade fixes \U inside heredocs,
        // this test flips green and tells us we can drop the sanitizer.
        java.nio.file.Path file = java.nio.file.Paths.get(
                "D:", "Git", "customer-portal",
                "ai-agent", "tools", "gerar-proposta-pdf.groovy");
        if (!java.nio.file.Files.exists(file)) return;
        String src = java.nio.file.Files.readString(file);
        assertThatThrownBy(() -> parse(src))
                .isInstanceOf(MultipleCompilationErrorsException.class)
                .hasMessageContaining("Unexpected character: '\\''")
                .hasMessageContaining("line 477, column 36");
    }

    @Test
    @DisplayName("heredoc inside if-block at script level — the inline pattern we chose")
    void heredocInsideIfBlockAtScriptLevel_parses() {
        // Refactor strategy that replaces the failing function: keep the
        // heredoc INSIDE the if-block but at script execution scope
        // (not inside a `def method() {}`). Pinning this to confirm the
        // inline approach is parser-safe before we ship it.
        String src = ""
                + "def branch = \"b2b\"\n"
                + "if (branch == \"b2b\") {\n"
                + "    def pythonScript = '''\n"
                + "import datetime\n"
                + "EMPRESA = INPUTS[\"empresa\"]\n"
                + "SHARE_URL = INPUTS.get(\"share_url_b2b\") or \"\"\n"
                + "'''\n"
                + "    return pythonScript\n"
                + "}\n"
                + "return \"\"";
        assertThatCode(() -> parse(src)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("two consecutive script-level heredocs — both heredocs strategy")
    void twoHeredocsAtScriptLevel_parses() {
        // Alternative strategy: declare both heredocs at script level
        // before the branch detection, then route below. Pinning the
        // double-heredoc declaration as parser-safe.
        String src = ""
                + "def B2B_PYTHON = '''\n"
                + "import datetime\n"
                + "EMPRESA = INPUTS[\"empresa\"]\n"
                + "'''\n"
                + "def B2C_PYTHON = '''\n"
                + "import datetime\n"
                + "NAME = INPUTS[\"name\"]\n"
                + "'''\n"
                + "return B2B_PYTHON + B2C_PYTHON";
        assertThatCode(() -> parse(src)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("compiled script actually runs — sanity check end-to-end")
    void inlineHeredocCompiledScript_runs() throws Exception {
        // Beyond parsing, prove the inline pattern produces a runnable
        // Script object and returns the expected heredoc body. Anchors
        // the fix to behavior, not just compilation success.
        String src = ""
                + "def branch = \"b2b\"\n"
                + "if (branch == \"b2b\") {\n"
                + "    def pythonScript = '''\n"
                + "hello b2b\n"
                + "'''\n"
                + "    return pythonScript.trim()\n"
                + "}\n"
                + "return \"\"";
        Class<? extends Script> clazz = parse(src);
        Script script = clazz.getDeclaredConstructor().newInstance();
        Object result = script.run();
        assertThat(result).isEqualTo("hello b2b");
    }
}
