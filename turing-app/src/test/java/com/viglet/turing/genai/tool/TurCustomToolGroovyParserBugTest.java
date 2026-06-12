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
    @DisplayName("bisect: production file with B2B heredoc body emptied")
    void productionFile_bisect_emptyB2BBody() throws Exception {
        // Hypothesis 1: bug is INSIDE the B2B heredoc body (lines 471-828).
        // If we empty it and the parse passes, hypothesis confirmed.
        java.nio.file.Path file = java.nio.file.Paths.get(
                "D:", "Git", "customer-portal",
                "ai-agent", "tools", "gerar-proposta-pdf.groovy");
        if (!java.nio.file.Files.exists(file)) return;
        String src = java.nio.file.Files.readString(file);
        String[] lines = src.split("\n", -1);
        StringBuilder reduced = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            int oneBased = i + 1;
            if (oneBased >= 471 && oneBased <= 828) continue; // drop B2B body
            reduced.append(lines[i]).append('\n');
        }
        try {
            parse(reduced.toString());
            System.out.println("BISECT [empty B2B body]: PARSED OK — bug IS in B2B body");
        } catch (MultipleCompilationErrorsException e) {
            System.out.println("BISECT [empty B2B body]: STILL FAILS — bug is NOT in B2B body alone");
            System.out.println(e.getMessage());
        }
    }

    @Test
    @DisplayName("bisect: production file with B2C heredoc body emptied")
    void productionFile_bisect_emptyB2CBody() throws Exception {
        // Hypothesis 2: bug is INSIDE the B2C heredoc body (lines 102-430).
        // The B2C heredoc was untouched but maybe contains a char that
        // throws the lexer's quote-pairing logic.
        java.nio.file.Path file = java.nio.file.Paths.get(
                "D:", "Git", "customer-portal",
                "ai-agent", "tools", "gerar-proposta-pdf.groovy");
        if (!java.nio.file.Files.exists(file)) return;
        String src = java.nio.file.Files.readString(file);
        String[] lines = src.split("\n", -1);
        StringBuilder reduced = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            int oneBased = i + 1;
            if (oneBased >= 102 && oneBased <= 430) continue; // drop B2C body
            reduced.append(lines[i]).append('\n');
        }
        try {
            parse(reduced.toString());
            System.out.println("BISECT [empty B2C body]: PARSED OK — bug IS in B2C body");
        } catch (MultipleCompilationErrorsException e) {
            System.out.println("BISECT [empty B2C body]: STILL FAILS — bug is NOT in B2C body alone");
            System.out.println(e.getMessage());
        }
    }

    @Test
    @DisplayName("bisect: B2B body line-by-line — find the single bad line")
    void productionFile_bisect_findBadLine() throws Exception {
        // We know the bug is in lines 471-828 (B2B body). Do a linear scan:
        // for each line N in that range, replace ONLY line N with a blank,
        // re-parse, and see if the parse succeeds. The first N that "fixes"
        // the parse is the offender.
        java.nio.file.Path file = java.nio.file.Paths.get(
                "D:", "Git", "customer-portal",
                "ai-agent", "tools", "gerar-proposta-pdf.groovy");
        if (!java.nio.file.Files.exists(file)) return;
        String src = java.nio.file.Files.readString(file);
        String[] lines = src.split("\n", -1);
        java.util.List<Integer> offenders = new java.util.ArrayList<>();
        for (int badLine = 471; badLine <= 828; badLine++) {
            String[] copy = lines.clone();
            copy[badLine - 1] = "";
            String reduced = String.join("\n", copy);
            try {
                parse(reduced);
                offenders.add(badLine);
                System.out.println("Removing line " + badLine + " makes parse SUCCEED: "
                        + lines[badLine - 1]);
                if (offenders.size() >= 5) break;
            } catch (MultipleCompilationErrorsException e) {
                // still fails — line not the offender
            }
        }
        if (offenders.isEmpty()) {
            System.out.println("No single line — the bug spans multiple lines or is interactive");
        }
    }

    @Test
    @DisplayName("minimal repro: emoji-range literals inside a triple-quote heredoc")
    void emojiRangeLiteralsInsideHeredoc_breaksParser() {
        // Reduced repro of the production bug. The variation selector
        // U+FE0F and the high-Unicode emoji-range literals on a single
        // line inside a ''' heredoc make the ANTLR4 lexer lose count of
        // open quotes — it later flags the NEXT heredoc opener as an
        // "Unexpected character: '\''" even though that opener is fine
        // in isolation.
        String src = ""
                + "def x = '''\n"
                + "r\"^[\\U0001F000-\\U0001FFFF☀-➿⌀-⏿]️?\\s*\"\n"
                + "'''\n"
                + "def y = '''\n"
                + "hello\n"
                + "'''\n"
                + "return x + y";
        // We don't assert success/failure here — we just want to log what
        // happens so the bug is visible in test output. Use the literal
        // chars (NOT escapes) to match what's actually in the source file.
        try {
            parse(src);
            System.out.println("MIN REPRO [unicode escapes]: PARSED OK");
        } catch (MultipleCompilationErrorsException e) {
            System.out.println("MIN REPRO [unicode escapes]: FAILED — " + e.getMessage());
        }
    }

    @Test
    @DisplayName("minimal repro: same content using literal unicode chars (the actual bug)")
    void emojiRangeLiteralsInsideHeredoc_literalChars_breaksParser() {
        // Same as above but with the literal characters (matches what
        // sits in gerar-proposta-pdf.groovy line 575).
        String src = ""
                + "def x = '''\n"
                + "r\"^[\\U0001F000-\\U0001FFFF☀-➿⌀-⏿]️?\\s*\"\n"
                + "'''\n"
                + "def y = '''\n"
                + "hello\n"
                + "'''\n"
                + "return x + y";
        try {
            parse(src);
            System.out.println("MIN REPRO [literal chars]: PARSED OK");
        } catch (MultipleCompilationErrorsException e) {
            System.out.println("MIN REPRO [literal chars]: FAILED — " + e.getMessage());
        }
    }

    @Test
    @DisplayName("isolate: which exact codepoint kills the lexer?")
    void isolateOffendingCodepoint() {
        // The offender is one of: U+2600 (☀), U+27BF (➿), U+2300 (⌀),
        // U+23FF (⏿), U+FE0F (variation selector), or the high U+1F000+
        // range (used as \\U escapes inside the Python string). Test each
        // alone inside a heredoc to find which one(s) flip the lexer.
        String[] cps = {
                "☀", // ☀
                "➿", // ➿
                "⌀", // ⌀
                "⏿", // ⏿
                "️", // variation selector-16 (invisible)
                "😀", // U+1F600 surrogate pair
        };
        String[] labels = {"U+2600 sun", "U+27BF arrow", "U+2300 diam-cross",
                "U+23FF black-square", "U+FE0F variation-selector",
                "U+1F600 surrogate-pair"};
        for (int i = 0; i < cps.length; i++) {
            String src = ""
                    + "def x = '''\n"
                    + "anything " + cps[i] + " more\n"
                    + "'''\n"
                    + "def y = '''\n"
                    + "second heredoc\n"
                    + "'''\n"
                    + "return x + y";
            try {
                parse(src);
                System.out.println(labels[i] + ": parses OK");
            } catch (MultipleCompilationErrorsException e) {
                System.out.println(labels[i] + ": BREAKS PARSER");
            }
        }
    }

    @Test
    @DisplayName("isolate: progressive regex variants — find the combo that breaks")
    void isolateProgressiveCombos() {
        String[] payloads = {
                "r\"^[\\U0001F000-\\U0001FFFF]\\s*\"",
                "r\"^[\\U0001F000-\\U0001FFFF☀-➿]\\s*\"",
                "r\"^[\\U0001F000-\\U0001FFFF☀-➿⌀-⏿]\\s*\"",
                "r\"^[\\U0001F000-\\U0001FFFF☀-➿⌀-⏿]?\\s*\"",
                "r\"^[\\U0001F000-\\U0001FFFF☀-➿⌀-⏿]️\\s*\"",  // + VS-16
                "r\"^[\\U0001F000-\\U0001FFFF☀-➿⌀-⏿]️?\\s*\"", // full
        };
        for (int i = 0; i < payloads.length; i++) {
            String src = ""
                    + "def x = '''\n"
                    + payloads[i] + "\n"
                    + "'''\n"
                    + "def y = '''\n"
                    + "tail\n"
                    + "'''\n"
                    + "return x + y";
            try {
                parse(src);
                System.out.println("Combo " + (i + 1) + ": parses OK -> " + payloads[i]);
            } catch (MultipleCompilationErrorsException e) {
                System.out.println("Combo " + (i + 1) + ": BREAKS    -> " + payloads[i]);
            }
        }
    }

    @Test
    @DisplayName("ROOT CAUSE: \\u0027 inside ''' heredoc — Unicode pre-processor runs first")
    void unicodeEscapePreProcessorIsTheBug() {
        // Java and Groovy run a Unicode escape pre-processor over the
        // source BEFORE the lexer. \\u0027 (single quote) inside ANY
        // context — comment, string, heredoc — becomes a literal '
        // character at the lexer level. Inside a ''' heredoc that turns
        // into a ' that closes the heredoc prematurely. The production
        // file's `\U0001F000` does this because the case-insensitive
        // \\u match captures it.
        String src = ""
                + "def x = '''\n"
                + "embedded \\u0027 here\n"
                + "'''\n"
                + "def y = '''\n"
                + "tail heredoc\n"
                + "'''\n"
                + "return x + y";
        try {
            parse(src);
            System.out.println("PRE-PROC [\\u0027 in heredoc]: parses OK");
        } catch (MultipleCompilationErrorsException e) {
            System.out.println("PRE-PROC [\\u0027 in heredoc]: BREAKS — confirmed Unicode pre-proc bug");
            System.out.println(e.getMessage());
        }
    }

    @Test
    @DisplayName("FIX: same content with double-backslash escapes Unicode pre-proc")
    void doubleBackslash_escapesPreProc_parses() {
        // The fix is to write `\\\\u` (double backslash) at the source
        // level, which lands as `\\u` after Java string literal escaping
        // — the pre-processor doesn't fire on a backslash-escaped \\u.
        // Or: only use \\u with valid 4-hex-digit escapes that produce
        // safe chars (not ', ", or \\n).
        String src = ""
                + "def x = '''\n"
                + "embedded \\\\u0027 here\n"
                + "'''\n"
                + "def y = '''\n"
                + "tail heredoc\n"
                + "'''\n"
                + "return x + y";
        try {
            parse(src);
            System.out.println("FIX [\\\\u escape]: parses OK");
        } catch (MultipleCompilationErrorsException e) {
            System.out.println("FIX [\\\\u escape]: BREAKS — " + e.getMessage());
        }
    }

    @Test
    @DisplayName("isolate: try variations to nail the exact trigger")
    void isolateExactTrigger() {
        String[] payloads = {
                "abc",
                "\\U0001F000",                          // bare \\U + 8 hex
                "\\U0001F000-\\U0001FFFF",              // range pattern
                "[\\U0001F000]",                        // wrapped in []
                "[\\U0001F000-\\U0001FFFF]",            // range in []
                "r\"[\\U0001F000-\\U0001FFFF]\"",       // raw string literal
                "r\"^[\\U0001F000-\\U0001FFFF]\\s*\"",  // production combo1
                "\\uFFFF",                              // lowercase \\u with valid hex
                "\\u0027",                              // lowercase \\u → '
        };
        for (int i = 0; i < payloads.length; i++) {
            String src = ""
                    + "def x = '''\n"
                    + payloads[i] + "\n"
                    + "'''\n"
                    + "def y = '''\n"
                    + "tail\n"
                    + "'''\n"
                    + "return x + y";
            try {
                parse(src);
                System.out.println("OK     -> " + payloads[i]);
            } catch (MultipleCompilationErrorsException e) {
                System.out.println("BREAKS -> " + payloads[i]);
            }
        }
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
    @DisplayName("FIX in action: production file with \\U → \\\\U pre-processing parses")
    void productionFile_withFix_parses() throws Exception {
        java.nio.file.Path file = java.nio.file.Paths.get(
                "D:", "Git", "customer-portal",
                "ai-agent", "tools", "gerar-proposta-pdf.groovy");
        if (!java.nio.file.Files.exists(file)) return;
        String src = java.nio.file.Files.readString(file);
        // Apply the sanitization that backend would do
        String sanitized = src.replaceAll("(?<!\\\\)\\\\U", "\\\\\\\\U");
        try {
            parse(sanitized);
            System.out.println("FIX [production with \\U → \\\\U]: parses OK");
        } catch (MultipleCompilationErrorsException e) {
            System.out.println("FIX [production with \\U → \\\\U]: STILL FAILS");
            System.out.println(e.getMessage());
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
        assertThat(TurCustomToolCallbackService.sanitizeGroovySource(null)).isEqualTo("");
        assertThat(TurCustomToolCallbackService.sanitizeGroovySource("")).isEqualTo("");
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
