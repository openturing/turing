import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { defineFlow } from "../src/define-flow.js";
import { FlowSpecError, transpileFlow } from "../src/transpile.js";
describe("transpileFlow", () => {
    it("emits the editor's wire shape with auto-layout and synthesized edge ids", () => {
        const transpiled = transpileFlow(defineFlow({
            name: "Smoke",
            nodes: [
                { id: "start", type: "start" },
                {
                    id: "ask-name",
                    type: "aiQuestion",
                    aiInstruction: "Ask name.",
                    outputVariable: "name",
                },
                { id: "end", type: "end" },
            ],
            edges: [
                { source: "start", target: "ask-name" },
                { source: "ask-name", target: "end" },
            ],
        }));
        // Top-level defaults applied.
        assert.equal(transpiled.guardrailMethod, "LLM_JUDGE");
        assert.equal(transpiled.triggerMode, "ONCE");
        assert.equal(transpiled.triggerLanguage, "AUTO");
        assert.equal(transpiled.description, null);
        assert.equal(transpiled.triggerDescription, null);
        // Node positions waterfall vertically, default labels filled.
        assert.deepEqual(transpiled.graph.nodes[0]?.position, { x: 0, y: 0 });
        assert.deepEqual(transpiled.graph.nodes[1]?.position, { x: 0, y: 120 });
        assert.equal(transpiled.graph.nodes[0]?.data.label, "START");
        assert.equal(transpiled.graph.nodes[1]?.data.label, "AI QUESTION");
        // Edge ids synthesized e1/e2 when omitted.
        assert.equal(transpiled.graph.edges[0]?.id, "e1");
        assert.equal(transpiled.graph.edges[1]?.id, "e2");
        assert.equal(transpiled.graph.edges[0]?.sourceHandle, null);
    });
    it("copies only whitelisted data fields per node type", () => {
        const transpiled = transpileFlow({
            name: "Whitelist",
            nodes: [
                { id: "start", type: "start" },
                {
                    id: "ask",
                    type: "aiQuestion",
                    aiInstruction: "x",
                    outputVariable: "name",
                    validationRule: "email",
                    inlineOptions: ["Yes", "No"],
                },
                { id: "end", type: "end" },
            ],
            edges: [
                { source: "start", target: "ask" },
                { source: "ask", target: "end" },
            ],
        });
        const askNode = transpiled.graph.nodes[1];
        assert.ok(askNode, "ask node present");
        const askData = askNode.data;
        assert.equal(askData.aiInstruction, "x");
        assert.equal(askData.outputVariable, "name");
        assert.equal(askData.validationRule, "email");
        assert.deepEqual(askData.inlineOptions, ["Yes", "No"]);
        // No `slotName` or other unrelated fields leaked through.
        assert.equal(askData.slotName, undefined);
    });
    it("carries formCapture formFields through to the wire shape (T107)", () => {
        const transpiled = transpileFlow({
            name: "Native form",
            nodes: [
                { id: "start", type: "start" },
                {
                    id: "lead-form",
                    type: "formCapture",
                    aiInstruction: "Fill in your details",
                    formFields: [
                        { name: "name", label: "Name", type: "text", required: true },
                        {
                            name: "email",
                            label: "Email",
                            type: "email",
                            required: true,
                            validationRule: "email",
                        },
                        {
                            name: "role",
                            label: "Role",
                            type: "select",
                            required: false,
                            options: ["Student", "Teacher"],
                        },
                    ],
                },
                { id: "end", type: "end" },
            ],
            edges: [
                { source: "start", target: "lead-form" },
                { source: "lead-form", target: "end" },
            ],
        });
        const formNode = transpiled.graph.nodes[1];
        assert.ok(formNode, "form node present");
        const formData = formNode.data;
        const fields = formData.formFields;
        assert.equal(fields.length, 3);
        assert.equal(fields[0]?.name, "name");
        assert.equal(fields[1]?.validationRule, "email");
        assert.deepEqual(fields[2]?.options, ["Student", "Teacher"]);
        assert.equal(fields[2]?.required, false);
    });
    it("validates condition branches", () => {
        assert.throws(() => transpileFlow({
            name: "Bad condition",
            nodes: [
                { id: "start", type: "start" },
                { id: "cond", type: "condition", conditionExpression: "x" },
                { id: "end", type: "end" },
            ],
            // Missing yes/no sourceHandles on the outgoing edges.
            edges: [
                { source: "start", target: "cond" },
                { source: "cond", target: "end" },
            ],
        }), (err) => err instanceof FlowSpecError && err.message.includes("Condition node 'cond'"));
    });
    it("validates switch sourceHandle ids", () => {
        assert.throws(() => transpileFlow({
            name: "Bad switch",
            nodes: [
                { id: "start", type: "start" },
                {
                    id: "sw",
                    type: "switch",
                    switchVariable: "answer",
                    switchOptions: [
                        { id: "opt-yes", label: "Yes" },
                        { id: "opt-no", label: "No" },
                    ],
                },
                { id: "end", type: "end" },
            ],
            edges: [
                { source: "start", target: "sw" },
                // Typo: opt-yess does not match opt-yes.
                { source: "sw", target: "end", sourceHandle: "opt-yess" },
            ],
        }), (err) => err instanceof FlowSpecError && err.message.includes("Switch node 'sw'"));
    });
    it("rejects duplicate node ids", () => {
        assert.throws(() => transpileFlow({
            name: "Dup",
            nodes: [
                { id: "start", type: "start" },
                { id: "dup", type: "aiQuestion", aiInstruction: "x", outputVariable: "n" },
                { id: "dup", type: "end" },
            ],
            edges: [
                { source: "start", target: "dup" },
            ],
        }), (err) => err instanceof FlowSpecError && err.message.includes("Duplicate node id"));
    });
    it("rejects missing or duplicate start nodes", () => {
        assert.throws(() => transpileFlow({
            name: "NoStart",
            nodes: [
                { id: "ask", type: "aiQuestion", aiInstruction: "x", outputVariable: "n" },
                { id: "end", type: "end" },
            ],
            edges: [{ source: "ask", target: "end" }],
        }), (err) => err instanceof FlowSpecError && err.message.includes("'start'"));
        assert.throws(() => transpileFlow({
            name: "TwoStarts",
            nodes: [
                { id: "s1", type: "start" },
                { id: "s2", type: "start" },
                { id: "end", type: "end" },
            ],
            edges: [
                { source: "s1", target: "end" },
                { source: "s2", target: "end" },
            ],
        }), (err) => err instanceof FlowSpecError && err.message.includes("2 'start' nodes"));
    });
    it("rejects dangling edge endpoints", () => {
        assert.throws(() => transpileFlow({
            name: "Dangling",
            nodes: [
                { id: "start", type: "start" },
                { id: "end", type: "end" },
            ],
            edges: [{ source: "start", target: "ghost" }],
        }), (err) => err instanceof FlowSpecError && err.message.includes("'ghost'"));
    });
    it("flags unreachable nodes (no incoming edges)", () => {
        assert.throws(() => transpileFlow({
            name: "Unreachable",
            nodes: [
                { id: "start", type: "start" },
                { id: "orphan", type: "aiQuestion", aiInstruction: "x", outputVariable: "n" },
                { id: "end", type: "end" },
            ],
            edges: [{ source: "start", target: "end" }],
        }), (err) => err instanceof FlowSpecError && err.message.includes("orphan"));
    });
    it("auto-declares slots referenced by outputVariable", () => {
        const transpiled = transpileFlow({
            name: "Auto slots",
            slots: [{ name: "explicit", type: "STRING" }],
            nodes: [
                { id: "start", type: "start" },
                {
                    id: "ask",
                    type: "aiQuestion",
                    aiInstruction: "x",
                    outputVariable: "auto",
                },
                { id: "end", type: "end" },
            ],
            edges: [
                { source: "start", target: "ask" },
                { source: "ask", target: "end" },
            ],
        });
        const names = (transpiled.slots ?? []).map((s) => s.name).sort();
        assert.deepEqual(names, ["auto", "explicit"]);
    });
    it("matches the editor's import-shape for a full lead-capture flow", () => {
        const transpiled = transpileFlow(defineFlow({
            name: "Lead capture",
            triggerLanguage: "PT",
            nodes: [
                { id: "start", type: "start" },
                {
                    id: "ask-email",
                    type: "aiQuestion",
                    aiInstruction: "Pergunte o email.",
                    outputVariable: "email",
                    validationRule: "email",
                },
                {
                    id: "decide-corp",
                    type: "condition",
                    conditionExpression: "email is corporate",
                },
                { id: "end-corp", type: "end" },
                { id: "end-personal", type: "end" },
            ],
            edges: [
                { source: "start", target: "ask-email" },
                { source: "ask-email", target: "decide-corp" },
                { source: "decide-corp", target: "end-corp", sourceHandle: "yes" },
                { source: "decide-corp", target: "end-personal", sourceHandle: "no" },
            ],
        }));
        // Spot-check: the structure round-trips into the editor's expected
        // graph shape with explicit yes/no handles on the condition edges.
        const yesEdge = transpiled.graph.edges.find((e) => e.sourceHandle === "yes");
        const noEdge = transpiled.graph.edges.find((e) => e.sourceHandle === "no");
        assert.ok(yesEdge, "yes edge present");
        assert.ok(noEdge, "no edge present");
        assert.equal(yesEdge?.target, "end-corp");
        assert.equal(noEdge?.target, "end-personal");
    });
    // T48 — scheduleAgent: routine fields copied, default label applied,
    // timeout edge stays as-is so the engine can route via sourceHandle:"timeout".
    it("transpiles scheduleAgent nodes with routine fields and timeout edge", () => {
        const transpiled = transpileFlow({
            name: "Proposal flow",
            nodes: [
                { id: "start", type: "start" },
                {
                    id: "fire-proposal",
                    type: "scheduleAgent",
                    routineId: "r-1",
                    routineName: "generate_proposal_pdf",
                    outputVariable: "proposta_pdf_url",
                    aiInstruction: '{"cargo":"{{cargo}}"}',
                    routineTimeoutMs: 120_000,
                },
                { id: "ok", type: "end" },
                { id: "fallback", type: "end" },
            ],
            edges: [
                { source: "start", target: "fire-proposal" },
                { source: "fire-proposal", target: "ok" },
                { source: "fire-proposal", target: "fallback", sourceHandle: "timeout" },
            ],
        });
        const scheduleNode = transpiled.graph.nodes.find((n) => n.id === "fire-proposal");
        assert.ok(scheduleNode, "scheduleAgent node present");
        const data = scheduleNode.data;
        assert.equal(data.label, "SCHEDULED ROUTINE");
        assert.equal(data.routineId, "r-1");
        assert.equal(data.routineName, "generate_proposal_pdf");
        assert.equal(data.outputVariable, "proposta_pdf_url");
        assert.equal(data.aiInstruction, '{"cargo":"{{cargo}}"}');
        assert.equal(data.routineTimeoutMs, 120_000);
        // Timeout edge preserved.
        const timeoutEdge = transpiled.graph.edges.find((e) => e.source === "fire-proposal" && e.sourceHandle === "timeout");
        assert.ok(timeoutEdge, "timeout edge preserved");
        assert.equal(timeoutEdge?.target, "fallback");
    });
    // T47 — subFlowSwitch carries switchVariable + switchOptions with optional
    // subFlowId/subFlowName per option (descent target, not edge target).
    it("transpiles subFlowSwitch nodes with per-option subFlow links", () => {
        const transpiled = transpileFlow({
            name: "Lead router",
            nodes: [
                { id: "start", type: "start" },
                {
                    id: "route-by-segment",
                    type: "subFlowSwitch",
                    switchVariable: "segment",
                    switchOptions: [
                        { id: "opt-b2b", label: "B2B", subFlowId: "lead-b2b", subFlowName: "Lead B2B" },
                        { id: "opt-b2c", label: "B2C", subFlowId: "lead-b2c" },
                    ],
                },
                { id: "end", type: "end" },
            ],
            edges: [
                { source: "start", target: "route-by-segment" },
                { source: "route-by-segment", target: "end" },
            ],
        });
        const sfsNode = transpiled.graph.nodes.find((n) => n.id === "route-by-segment");
        assert.ok(sfsNode, "subFlowSwitch node present");
        const data = sfsNode.data;
        assert.equal(data.label, "SUB-FLOW SWITCH");
        assert.equal(data.switchVariable, "segment");
        const options = data.switchOptions;
        assert.equal(options.length, 2);
        const [optB2b, optB2c] = options;
        assert.ok(optB2b, "B2B option present");
        assert.ok(optB2c, "B2C option present");
        assert.equal(optB2b.subFlowId, "lead-b2b");
        assert.equal(optB2b.subFlowName, "Lead B2B");
        assert.equal(optB2c.subFlowId, "lead-b2c");
    });
});
//# sourceMappingURL=transpile.test.js.map