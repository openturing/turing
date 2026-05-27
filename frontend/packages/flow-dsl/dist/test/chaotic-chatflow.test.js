import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { defineFlow } from "../src/define-flow.js";
import { FlowSpecError, transpileBundle, transpileFlow } from "../src/transpile.js";
import { chaoticBundle } from "./fixtures/chaotic-chatflow.js";
/** Flatten every node's `type` across a transpiled bundle. */
function nodeTypes(bundle) {
    const types = new Set();
    for (const flow of bundle) {
        for (const node of flow.graph.nodes) {
            types.add(node.type);
        }
    }
    return types;
}
function nodeById(flow, id) {
    const node = flow.graph.nodes.find((n) => n.id === id);
    assert.ok(node, `node '${id}' present in '${flow.name}'`);
    return node;
}
describe("flow-dsl new capabilities", () => {
    it("transpiles a suspend node with the default label and no leaked data fields", () => {
        const flow = transpileFlow({
            name: "Suspend",
            nodes: [
                { id: "start", type: "start" },
                { id: "park", type: "suspend", label: "Awaiting approval" },
                { id: "end", type: "end" },
            ],
            edges: [
                { source: "start", target: "park" },
                { source: "park", target: "end" },
            ],
        });
        const park = nodeById(flow, "park");
        assert.equal(park.type, "suspend");
        assert.equal(park.data.label, "Awaiting approval");
        // Only label + type — suspend reads nothing else.
        assert.deepEqual(Object.keys(park.data).sort(), ["label", "type"]);
    });
    it("copies outputVariable + continueOnFailure onto a functionCall node", () => {
        const flow = transpileFlow({
            name: "FnFail",
            nodes: [
                { id: "start", type: "start" },
                {
                    id: "fn",
                    type: "functionCall",
                    toolSource: "NATIVE",
                    functionName: "do_thing",
                    aiInstruction: '{"x":"{{x}}"}',
                    outputVariable: "result",
                    continueOnFailure: true,
                },
                { id: "ok", type: "end" },
                { id: "recover", type: "end" },
            ],
            edges: [
                { source: "start", target: "fn" },
                { source: "fn", target: "ok" },
                { source: "fn", target: "recover", sourceHandle: "failure" },
            ],
        });
        const fn = nodeById(flow, "fn").data;
        assert.equal(fn.functionName, "do_thing");
        assert.equal(fn.outputVariable, "result");
        assert.equal(fn.continueOnFailure, true);
        // Failure edge preserved verbatim.
        const failureEdge = flow.graph.edges.find((e) => e.sourceHandle === "failure");
        assert.ok(failureEdge, "failure edge present");
        assert.equal(failureEdge?.target, "recover");
    });
    it("copies continueOnFailure onto a scheduleAgent node", () => {
        const flow = transpileFlow({
            name: "SchedFail",
            nodes: [
                { id: "start", type: "start" },
                {
                    id: "sched",
                    type: "scheduleAgent",
                    routineId: "r1",
                    outputVariable: "out",
                    routineTimeoutMs: 5000,
                    continueOnFailure: true,
                },
                { id: "end", type: "end" },
            ],
            edges: [
                { source: "start", target: "sched" },
                { source: "sched", target: "end" },
            ],
        });
        const sched = nodeById(flow, "sched").data;
        assert.equal(sched.continueOnFailure, true);
        assert.equal(sched.routineTimeoutMs, 5000);
    });
    it("allows one blank-sourceHandle wildcard edge on a switch", () => {
        assert.doesNotThrow(() => transpileFlow({
            name: "SwitchWildcard",
            nodes: [
                { id: "start", type: "start" },
                {
                    id: "sw",
                    type: "switch",
                    switchVariable: "v",
                    switchOptions: [{ id: "opt-a", label: "A" }],
                },
                { id: "a", type: "end" },
                { id: "fallback", type: "end" },
            ],
            edges: [
                { source: "start", target: "sw" },
                { source: "sw", target: "a", sourceHandle: "opt-a" },
                { source: "sw", target: "fallback" }, // wildcard — must be allowed
            ],
        }));
    });
    it("still rejects a non-blank switch sourceHandle that matches no option", () => {
        assert.throws(() => transpileFlow({
            name: "BadSwitch",
            nodes: [
                { id: "start", type: "start" },
                { id: "sw", type: "switch", switchVariable: "v", switchOptions: [{ id: "opt-a", label: "A" }] },
                { id: "end", type: "end" },
            ],
            edges: [
                { source: "start", target: "sw" },
                { source: "sw", target: "end", sourceHandle: "opt-typo" },
            ],
        }), (err) => err instanceof FlowSpecError && err.message.includes("opt-typo"));
    });
    it("rejects more than one wildcard edge on a switch", () => {
        assert.throws(() => transpileFlow({
            name: "TwoWildcards",
            nodes: [
                { id: "start", type: "start" },
                { id: "sw", type: "switch", switchVariable: "v", switchOptions: [{ id: "opt-a", label: "A" }] },
                { id: "x", type: "end" },
                { id: "y", type: "end" },
            ],
            edges: [
                { source: "start", target: "sw" },
                { source: "sw", target: "x" },
                { source: "sw", target: "y" },
            ],
        }), (err) => err instanceof FlowSpecError && err.message.includes("wildcard"));
    });
    it("preserves the enriched persona shape through transpile", () => {
        const flow = transpileFlow({
            name: "Personas",
            personas: [
                {
                    id: "P1",
                    name: "Voice One",
                    systemInstruction: "Be brief.",
                    tone: "EXECUTIVE",
                    verbosity: 2,
                    languageStyle: "PERSUASIVE",
                    mandatoryTerms: "deal|now",
                    forbiddenTerms: "maybe",
                    enabled: 1,
                },
            ],
            nodes: [
                { id: "start", type: "start" },
                { id: "v", type: "persona", personaId: "P1" },
                { id: "end", type: "end" },
            ],
            edges: [
                { source: "start", target: "v" },
                { source: "v", target: "end" },
            ],
        });
        const persona = flow.personas?.[0];
        assert.ok(persona, "persona declared");
        assert.equal(persona?.id, "P1");
        assert.equal(persona?.systemInstruction, "Be brief.");
        assert.equal(persona?.tone, "EXECUTIVE");
        assert.equal(persona?.languageStyle, "PERSUASIVE");
    });
});
describe("transpileBundle", () => {
    it("validates cross-flow subFlowId references and fills subFlowName", () => {
        const bundle = transpileBundle([
            defineFlow({
                id: "main",
                name: "Main",
                nodes: [
                    { id: "start", type: "start" },
                    { id: "go", type: "subFlow", subFlowId: "child" },
                    { id: "end", type: "end" },
                ],
                edges: [
                    { source: "start", target: "go" },
                    { source: "go", target: "end" },
                ],
            }),
            defineFlow({
                id: "child",
                name: "Child Flow",
                nodes: [
                    { id: "start", type: "start" },
                    { id: "end", type: "end" },
                ],
                edges: [{ source: "start", target: "end" }],
            }),
        ]);
        assert.equal(bundle.length, 2);
        const go = bundle[0]?.graph.nodes.find((n) => n.id === "go");
        assert.equal((go?.data).subFlowName, "Child Flow");
    });
    it("throws on a dangling subFlowId reference", () => {
        assert.throws(() => transpileBundle([
            defineFlow({
                id: "main",
                name: "Main",
                nodes: [
                    { id: "start", type: "start" },
                    { id: "go", type: "subFlow", subFlowId: "ghost" },
                    { id: "end", type: "end" },
                ],
                edges: [
                    { source: "start", target: "go" },
                    { source: "go", target: "end" },
                ],
            }),
        ]), (err) => err instanceof FlowSpecError && err.message.includes("ghost"));
    });
    it("throws when a bundled flow has no id", () => {
        assert.throws(() => transpileBundle([
            defineFlow({
                name: "NoId",
                nodes: [
                    { id: "start", type: "start" },
                    { id: "end", type: "end" },
                ],
                edges: [{ source: "start", target: "end" }],
            }),
        ]), (err) => err instanceof FlowSpecError && err.message.includes("no id"));
    });
    it("validates subFlowSwitch option subFlowId references too", () => {
        assert.throws(() => transpileBundle([
            defineFlow({
                id: "main",
                name: "Main",
                nodes: [
                    { id: "start", type: "start" },
                    {
                        id: "sfs",
                        type: "subFlowSwitch",
                        switchVariable: "v",
                        switchOptions: [{ id: "o", label: "O", subFlowId: "nope" }],
                    },
                    { id: "end", type: "end" },
                ],
                edges: [
                    { source: "start", target: "sfs" },
                    { source: "sfs", target: "end" },
                ],
            }),
        ]), (err) => err instanceof FlowSpecError && err.message.includes("nope"));
    });
});
describe("chaotic-chatflow bundle (the giant stress fixture)", () => {
    const bundle = transpileBundle([...chaoticBundle]);
    it("transpiles all seven flows", () => {
        assert.equal(bundle.length, 7);
        assert.deepEqual(bundle.map((f) => f.id), [
            "chaos-main",
            "chaos-sandwich",
            "chaos-abyss",
            "chaos-paperwork",
            "chaos-ouroboros",
            "chaos-scheduler",
            "chaos-suspend",
        ]);
    });
    it("exercises EVERY node type the engine supports", () => {
        const expected = [
            "start",
            "end",
            "aiQuestion",
            "formCapture",
            "condition",
            "functionCall",
            "scheduleAgent",
            "subFlow",
            "subFlowSwitch",
            "persona",
            "switch",
            "slot",
            "writeSlot",
            "suspend",
        ];
        const present = nodeTypes(bundle);
        for (const type of expected) {
            assert.ok(present.has(type), `bundle is missing node type '${type}'`);
        }
    });
    it("declares one persona per (tone, style) register on the main flow", () => {
        const main = bundle[0];
        assert.ok(main?.personas, "main flow declares personas");
        assert.equal(main?.personas?.length, 5);
        const tones = new Set(main?.personas?.map((p) => p.tone));
        assert.deepEqual([...tones].sort(), ["CASUAL", "EXECUTIVE", "FORMAL", "TECHNICAL"]);
        const styles = new Set(main?.personas?.map((p) => p.languageStyle));
        assert.deepEqual([...styles].sort(), ["DIRECT", "INSTRUCTIONAL", "NARRATIVE", "NEUTRAL", "PERSUASIVE"]);
    });
    it("wires functionCall + scheduleAgent failure edges and a switch wildcard", () => {
        const main = bundle[0];
        assert.ok(main);
        const fnFailure = main.graph.edges.find((e) => e.source === "fn-finalize" && e.sourceHandle === "failure");
        assert.ok(fnFailure, "functionCall failure edge present");
        const schedTimeout = main.graph.edges.find((e) => e.source === "sched-finalize" && e.sourceHandle === "timeout");
        assert.ok(schedTimeout, "scheduleAgent timeout edge present");
        const wildcard = main.graph.edges.find((e) => e.source === "switch-wild" && (e.sourceHandle === null || e.sourceHandle === ""));
        assert.ok(wildcard, "switch wildcard edge present");
    });
    it("covers every onJudgeReject policy across aiQuestion nodes", () => {
        const policies = new Set();
        for (const node of bundle[0].graph.nodes) {
            const v = node.data.onJudgeReject;
            if (v !== undefined)
                policies.add(v);
        }
        assert.deepEqual([...policies].sort(), ["advance_with_literal", "block", "reprompt"]);
    });
    it("populates subFlowName on cross-flow references", () => {
        const main = bundle[0];
        const subPaper = main.graph.nodes.find((n) => n.id === "subflow-paperwork");
        assert.equal((subPaper?.data).subFlowName, "Stamp & Paperwork Office");
        const sandwich = bundle[1];
        const sfs = sandwich.graph.nodes.find((n) => n.id === "subflowswitch-abyss");
        const opts = (sfs?.data).switchOptions;
        const clubOpt = opts.find((o) => o.id === "opt-club");
        assert.equal(clubOpt?.subFlowName, "The Deep Abyss");
    });
});
//# sourceMappingURL=chaotic-chatflow.test.js.map