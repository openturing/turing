import { describe, expect, it } from "vitest";

import {
  catalogSupportsFileInput,
  catalogSupportsTools,
  isDeprecatedModel,
  sortModelOptions,
} from "../model-meta";
import type { TurLlmModelOption } from "@/models/llm/llm-model-option.model.ts";

const opt = (id: string, meta: TurLlmModelOption["metadata"]): TurLlmModelOption => ({
  id,
  label: id,
  kind: "CHAT",
  metadata: meta,
});

describe("sortModelOptions (T779)", () => {
  const a = opt("a", { pricing: { inputPer1M: 10 }, contextWindow: 8000, benchmarks: { intelligenceIndex: 50 } });
  const b = opt("b", { pricing: { inputPer1M: 2 }, contextWindow: 200000, benchmarks: { intelligenceIndex: 80 } });
  const noMeta = opt("z", null);

  it("keeps input order for relevance", () => {
    expect(sortModelOptions([a, b], "relevance").map((o) => o.id)).toEqual(["a", "b"]);
  });

  it("orders price ascending (cheapest first)", () => {
    expect(sortModelOptions([a, b], "price").map((o) => o.id)).toEqual(["b", "a"]);
  });

  it("orders context and intelligence descending (biggest/best first)", () => {
    expect(sortModelOptions([a, b], "context").map((o) => o.id)).toEqual(["b", "a"]);
    expect(sortModelOptions([a, b], "intelligence").map((o) => o.id)).toEqual(["b", "a"]);
  });

  it("sinks options missing the metric to the end", () => {
    expect(sortModelOptions([noMeta, a, b], "price").map((o) => o.id)).toEqual(["b", "a", "z"]);
  });
});

describe("catalogSupportsTools (T781)", () => {
  it("is undefined when capabilities are unknown", () => {
    expect(catalogSupportsTools(null)).toBeUndefined();
    expect(catalogSupportsTools({})).toBeUndefined();
    expect(catalogSupportsTools({ capabilities: [] })).toBeUndefined();
  });

  it("is true when tools capability is present (case-insensitive)", () => {
    expect(catalogSupportsTools({ capabilities: ["Tools", "vision"] })).toBe(true);
  });

  it("is false when capabilities are listed without tools", () => {
    expect(catalogSupportsTools({ capabilities: ["vision", "reasoning"] })).toBe(false);
  });
});

describe("catalogSupportsFileInput (T781)", () => {
  it("is undefined when neither capabilities nor modalities are known", () => {
    expect(catalogSupportsFileInput(null)).toBeUndefined();
    expect(catalogSupportsFileInput({})).toBeUndefined();
  });

  it("is true via the vision capability or a file-like input modality", () => {
    expect(catalogSupportsFileInput({ capabilities: ["vision"] })).toBe(true);
    expect(catalogSupportsFileInput({ modalities: { input: ["text", "image"] } })).toBe(true);
  });

  it("is false when info is present but no image/file signal", () => {
    expect(catalogSupportsFileInput({ capabilities: ["tools"], modalities: { input: ["text"] } })).toBe(false);
  });
});

describe("isDeprecatedModel (T782)", () => {
  it("is false for a live model", () => {
    expect(isDeprecatedModel(null)).toBe(false);
    expect(isDeprecatedModel({ status: "GA" })).toBe(false);
  });

  it("is true via the deprecated flag or a DEPRECATED/RETIRED status", () => {
    expect(isDeprecatedModel({ deprecated: true })).toBe(true);
    expect(isDeprecatedModel({ status: "deprecated" })).toBe(true);
    expect(isDeprecatedModel({ status: "RETIRED" })).toBe(true);
  });
});
