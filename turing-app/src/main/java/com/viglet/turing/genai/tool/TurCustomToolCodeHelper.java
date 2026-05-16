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

/**
 * Convenience helper exposed to Custom Tool Groovy scripts under the binding
 * variable name {@code code}. Wraps {@link TurCodeInterpreterToolService} so
 * customer-authored scripts can hand off heavy work (PDF rendering, charting,
 * data transformation) to the platform's sandboxed Python runtime without
 * re-implementing process management.
 *
 * <p>One instance is constructed per Custom Tool invocation by
 * {@link TurCustomToolCallbackService}, sharing the same singleton
 * {@link TurCodeInterpreterToolService} (the sandbox manager is stateless;
 * each call gets its own session directory).
 *
 * <p>Usage in Groovy:
 * <pre>{@code
 *   def result = code.executePython('''
 *       from reportlab.platypus import SimpleDocTemplate
 *       # ... build a PDF
 *       print('done')
 *   ''')
 *   // result is a markdown string from execute_python that includes
 *   // [Download proposta.pdf](/api/v2/code-interpreter/abc123/proposta.pdf)
 *   def m = (result =~ /\[Download[^\]]+]\(([^)]+)\)/)
 *   if (m) slots.set("proposta_pdf_url", m[0][1])
 * }</pre>
 *
 * <p>The result format follows {@link TurCodeInterpreterToolService#executePython(String)}:
 * stdout first, then a {@code --- Generated Files ---} section with markdown
 * link references when the script wrote any files to the session dir.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
 */
public class TurCustomToolCodeHelper {

    private final TurCodeInterpreterToolService codeInterpreter;

    public TurCustomToolCodeHelper(TurCodeInterpreterToolService codeInterpreter) {
        this.codeInterpreter = codeInterpreter;
    }

    /**
     * Executes a Python script in the platform sandbox and returns the
     * markdown result produced by {@link TurCodeInterpreterToolService}.
     * 30-second timeout enforced; longer scripts must split work or be
     * refactored.
     *
     * @param pythonCode complete script source. Inputs must be inlined
     *                   (the sandbox does not accept stdin); idiomatic
     *                   pattern is to dump the inputs as base64-encoded
     *                   JSON at the top of the script and {@code json.loads}
     *                   them inside.
     */
    public String executePython(String pythonCode) {
        if (pythonCode == null || pythonCode.isBlank()) {
            return "Error: empty Python code";
        }
        return codeInterpreter.executePython(pythonCode);
    }
}
