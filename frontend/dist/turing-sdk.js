var W = /* @__PURE__ */ new Set([
  "POST",
  "PUT",
  "PATCH",
  "DELETE"
]);
function $t(t) {
  return t.endsWith("/") ? t.slice(0, -1) : t;
}
function H() {
  if (typeof document > "u") return;
  const t = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/.exec(document.cookie);
  return t ? decodeURIComponent(t[1]) : void 0;
}
function we(t) {
  if (!t?.baseURL) throw new Error("createTuringClient: `baseURL` is required");
  const e = $t(t.baseURL), n = t.credentials ?? "include", s = {
    ...t.apiKey ? { Key: t.apiKey } : {},
    ...t.headers
  };
  async function r() {
    if (t.getCsrfToken) return t.getCsrfToken();
    const c = H();
    if (c) return c;
    try {
      const i = await fetch(`${e}/csrf`, { credentials: n });
      if (i.ok) return i.headers.get("X-XSRF-TOKEN") ?? H();
    } catch {
    }
    return H();
  }
  async function o(c, i = {}, l = W.has((i.method ?? "GET").toUpperCase())) {
    const u = {
      ...s,
      ...i.headers
    };
    if (l) {
      const p = await r();
      p && (u["X-XSRF-TOKEN"] = p);
    }
    return fetch(`${e}${c}`, {
      credentials: n,
      ...i,
      headers: u
    });
  }
  async function a(c, i, l = {}) {
    const u = c.toUpperCase(), p = { ...l.headers };
    let S;
    l.body !== void 0 && l.body !== null && (l.body instanceof FormData || l.body instanceof Blob || typeof l.body == "string" ? S = l.body : (S = JSON.stringify(l.body), p["Content-Type"] = "application/json"));
    const m = l.csrf ?? W.has(u), g = await o(i, {
      method: u,
      headers: p,
      body: S,
      signal: l.signal
    }, m);
    if (!g.ok) throw new Error(`HTTP ${g.status}: ${g.statusText}`);
    if (g.status === 204) return;
    const h = await g.text();
    if (h)
      try {
        return JSON.parse(h);
      } catch {
        return h;
      }
  }
  return {
    baseURL: e,
    credentials: n,
    request: a,
    get: (c, i) => a("GET", c, i),
    post: (c, i, l) => a("POST", c, {
      ...l,
      body: i
    }),
    del: (c, i) => a("DELETE", c, i),
    fetchRaw: o,
    ensureCsrfToken: r
  };
}
function st() {
  try {
    const t = Intl.DateTimeFormat().resolvedOptions().timeZone;
    return t ? { "X-Timezone": t } : {};
  } catch {
    return {};
  }
}
function rt(t) {
  const e = new URLSearchParams();
  e.append("q", t.q || "*"), t.p && e.append("p", t.p), t._setlocale && e.append("_setlocale", t._setlocale), t.sort && e.append("sort", t.sort);
  for (const n of t.fq ?? []) e.append("fq[]", n);
  for (const n of t.tr ?? []) e.append("tr[]", n);
  return t.nfpr && e.append("nfpr", t.nfpr), t.group && e.append("group", t.group), t.rows && e.append("rows", t.rows), e.toString().replaceAll("%5B%5D", "[]");
}
function ot(t, e, n) {
  return t.get(`/sn/${e}/search?${rt(n)}`);
}
async function At(t, e, n) {
  try {
    await t.post(`/sn/${e}/click`, n);
  } catch (s) {
    typeof console < "u" && console.warn("[Turing] click tracking failed", s);
  }
}
function at(t, e, n) {
  const s = new URLSearchParams();
  return s.append("q", n.q), n._setlocale && s.append("_setlocale", n._setlocale), t.get(`/sn/${e}/chat?${s}`);
}
var Ce = {
  NONE: "Chat com IA está disponível",
  NO_GENAI: "Site não tem GenAI configurado no admin do Turing",
  NO_AGENT: "Nenhum AI Agent vinculado a este site no admin do Turing",
  AGENT_DISABLED: "O AI Agent vinculado está desabilitado",
  RAG_DISABLED: "RAG está desabilitado no AI Agent (habilite no admin)",
  MISSING_LLM: "O AI Agent não tem nenhuma instância de LLM configurada",
  MISSING_EMBEDDING: "O AI Agent não tem um modelo de embedding selecionado",
  MISSING_STORE: "O AI Agent não tem um vector store selecionado"
};
function it(t, e) {
  return t.get(`/sn/${e}/chat/enabled`);
}
function Et(t) {
  try {
    const e = JSON.parse(t);
    if (Array.isArray(e)) {
      const n = e.filter((s) => typeof s == "string");
      if (n.length > 0) return n;
    }
  } catch {
  }
  return null;
}
function Ot(t) {
  try {
    const e = JSON.parse(t);
    if (e && Array.isArray(e.fields) && e.fields.length > 0) return e;
  } catch {
  }
  return null;
}
function kt(t) {
  try {
    const e = JSON.parse(t);
    if (Array.isArray(e) && e.length > 0) return e;
  } catch {
  }
  return null;
}
async function J(t, e, n, s, r) {
  if (!t.body) throw new Error("Chat response has no body");
  const o = t.body.getReader(), a = new TextDecoder();
  let c = "", i = "", l = [], u, p;
  function S(m) {
    if (!m.startsWith("data:")) return;
    const g = m.slice(5).trim();
    if (!g) return;
    let h;
    try {
      h = JSON.parse(g);
    } catch {
      return;
    }
    if (h.type === "options") {
      const f = Et(h.content);
      f && (l = f, n?.(f));
      return;
    }
    if (h.type === "form") {
      const f = Ot(h.content);
      f && (u = f, s?.(f));
      return;
    }
    if (h.type === "sources") {
      const f = kt(h.content);
      f && (p = f, r?.(f));
      return;
    }
    h.content && (i += h.content, e?.(h.content));
  }
  try {
    for (; ; ) {
      const { done: m, value: g } = await o.read();
      if (m) break;
      c += a.decode(g, { stream: !0 });
      const h = c.split(`
`);
      c = h.pop() ?? "";
      for (const f of h) S(f);
    }
    c && S(c);
  } finally {
    o.releaseLock();
  }
  return {
    text: i,
    options: l,
    form: u,
    sources: p
  };
}
async function ct(t, e, n, s, r) {
  const o = await t.fetchRaw(`/sn/${e}/chat/conversation`, {
    method: "POST",
    headers: {
      Accept: "text/event-stream",
      "Content-Type": "application/json",
      ...st()
    },
    body: JSON.stringify({
      messages: n,
      locale: s,
      conversationId: r?.conversationId,
      flowId: r?.flowId,
      forcedVariant: r?.forcedVariant
    }),
    signal: r?.signal
  });
  if (!o.ok) throw new Error(`HTTP ${o.status}: ${o.statusText}`);
  const { text: a, options: c, form: i, sources: l } = await J(o, r?.onToken, r?.onOptions, r?.onForm, r?.onSources);
  return {
    role: "assistant",
    content: a,
    options: c,
    form: i,
    sources: l
  };
}
async function lt(t, e, n, s, r) {
  const o = {
    llmInstanceId: n,
    messages: s,
    conversationId: r?.conversationId,
    flowId: r?.flowId,
    forcedVariant: r?.forcedVariant,
    selectedSkillId: r?.selectedSkillId
  }, a = {
    Accept: "text/event-stream",
    ...st()
  };
  let c;
  if (r?.files && r.files.length > 0) {
    const m = new FormData();
    m.append("request", new Blob([JSON.stringify(o)], { type: "application/json" }));
    for (const g of r.files) m.append("files", g);
    c = m;
  } else
    a["Content-Type"] = "application/json", c = JSON.stringify(o);
  const i = await t.fetchRaw(`/v2/ai-agent/${e}/chat`, {
    method: "POST",
    headers: a,
    body: c,
    signal: r?.signal
  });
  if (!i.ok) throw new Error(`HTTP ${i.status}: ${i.statusText}`);
  const { text: l, options: u, form: p, sources: S } = await J(i, r?.onToken, r?.onOptions, r?.onForm, r?.onSources);
  return {
    role: "assistant",
    content: l,
    options: u,
    form: p,
    sources: S
  };
}
function Rt(t, e, n, s, r) {
  return t.post(`/sn/${e}/chat/form-submit`, {
    conversationId: n,
    nodeId: r,
    values: s
  });
}
async function xt(t, e, n, s) {
  const r = { Accept: "text/event-stream" };
  let o;
  if (s?.files && s.files.length > 0) {
    const i = new FormData();
    i.append("request", new Blob([JSON.stringify({ messages: n })], { type: "application/json" }));
    for (const l of s.files) i.append("files", l);
    o = i;
  } else
    r["Content-Type"] = "application/json", o = JSON.stringify({ messages: n });
  const a = await t.fetchRaw(`/v2/llm/${e}/chat`, {
    method: "POST",
    headers: r,
    body: o,
    signal: s?.signal
  });
  if (!a.ok) throw new Error(`HTTP ${a.status}: ${a.statusText}`);
  const { text: c } = await J(a, s?.onToken);
  return {
    role: "assistant",
    content: c,
    options: []
  };
}
async function Lt(t, e, n, s) {
  const r = await t.fetchRaw(`/v2/llm/${e}/semantic-chat`, {
    method: "POST",
    headers: {
      Accept: "text/event-stream",
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ messages: n }),
    signal: s?.signal
  });
  if (!r.ok) throw new Error(`HTTP ${r.status}: ${r.statusText}`);
  const { text: o } = await J(r, s?.onToken);
  return {
    role: "assistant",
    content: o,
    options: []
  };
}
async function ut(t, e, n) {
  const s = new URLSearchParams({ conversationId: n }), r = await t.del(`/sn/${e}/chat/conversation-state?${s}`);
  return typeof r == "number" ? r : 0;
}
async function ft(t, e, n, s) {
  const r = new URLSearchParams({
    flowId: n,
    conversationId: s
  });
  return await t.del(`/v2/ai-agent/${e}/chat-flow-state?${r}`) === !0;
}
function Nt(t, e) {
  return t.get(`/sn/${e}/chat/intents`);
}
function dt(t, e, n) {
  const s = new URLSearchParams({ conversationId: n });
  return t.get(`/sn/${e}/chat/slots?${s}`);
}
function ht(t, e, n) {
  const s = new URLSearchParams({ conversationId: n });
  return t.get(`/v2/ai-agent/${e}/chat-slots?${s}`);
}
async function Ft(t, e, n, s, r) {
  return await t.post(`/sn/${e}/chat/slots`, {
    conversationId: n,
    name: s,
    value: r
  }) ?? { updatedStates: 0 };
}
async function _t(t, e, n, s, r) {
  const o = new FormData();
  return o.append("file", n), o.append("conversationId", s), r && r.length > 0 && o.append("slotNames", r.join(",")), await t.post(`/sn/${e}/chat/slot-extract`, o) ?? {
    extracted: {},
    slotsWritten: 0,
    extractedTextChars: 0
  };
}
function qt(t, e, n) {
  const s = new URLSearchParams({ conversationId: n });
  return t.get(`/sn/${e}/chat/state?${s}`);
}
async function Ut(t, e, n) {
  return await t.post(`/sn/${e}/chat/handoff`, {
    conversationId: n.conversationId,
    channel: n.channel,
    destination: n.destination,
    intro: n.intro,
    slotsToInclude: n.slotsToInclude
  }) ?? {
    url: null,
    transcript: null,
    slotsIncluded: 0
  };
}
async function Mt(t, e, n, s) {
  return await t.post(`/sn/${e}/chat/flow-select`, {
    conversationId: n,
    flow: s
  }) ?? {
    success: !1,
    pinnedFlowId: null,
    pinnedFlowName: null,
    reason: "empty response"
  };
}
function Pt(t) {
  return t.get("/llm");
}
function Dt(t, e) {
  return t.get(`/v2/llm/${e}/chat/context-info`);
}
function jt(t, e, n) {
  const s = new URLSearchParams({ llmInstanceId: n });
  return t.get(`/v2/ai-agent/${e}/chat/context-info?${s}`);
}
function St(t, e, n) {
  return t.get(`/sn/${e}/ac?${rt(n)}`);
}
function Jt(t, e) {
  return t.get(`/sn/${e}/search/sort-options`);
}
function pt(t, e, n) {
  const s = new URLSearchParams({ id: n.id });
  return n.rows != null && s.append("rows", String(n.rows)), n.locale && s.append("locale", n.locale), n.mode && s.append("mode", n.mode), t.get(`/sn/${e}/search/similar?${s}`);
}
async function Gt(t, e, n, s, r, o) {
  const a = new FormData();
  return a.append("file", r), a.append("conversationId", n), a.append("slotName", s), o?.vision && a.append("vision", "true"), o?.visionSlotNames && o.visionSlotNames.length > 0 && a.append("visionSlotNames", o.visionSlotNames.join(",")), await t.post(`/sn/${e}/chat/slot-upload`, a) ?? {
    slotName: s,
    slotType: "",
    objectName: "",
    url: "",
    contentType: "",
    size: 0,
    visionExtracted: {},
    visionSlotsWritten: 0,
    error: "empty response"
  };
}
async function Ht(t, e, n, s, r) {
  return await t.post(`/sn/${e}/chat/resume`, {
    conversationId: n,
    slotUpdates: s,
    resumeReason: r
  }) ?? {
    resumed: 0,
    wasParked: !1,
    error: "empty response"
  };
}
function Bt(t, e, n, s) {
  const r = new URLSearchParams({ q: s });
  return t.get(`/sn/${e}/${n}/spell-check?${r}`);
}
function Kt(t, e, n, s = "en") {
  const r = new URLSearchParams({ locale: s });
  return t.post(`/sn/${e}/_search?${r}`, n);
}
function zt(t, e, n, s) {
  const r = new URLSearchParams({ locale: s }), o = typeof n == "string" ? n : JSON.stringify(n);
  return t.post(`/sn/${e}/query?${r}`, o, { headers: { "Content-Type": "application/json" } });
}
function Vt(t) {
  return t.get("/discovery");
}
function Xt(t) {
  return t.get("/features");
}
function Wt(t) {
  return t.get("/locale");
}
function Qt(t) {
  return t.get("/llm/vendor");
}
async function Yt(t, e) {
  const n = e.regenerate ? "/v2/summary?regenerate=true" : "/v2/summary";
  return await t.post(n, {
    cacheKey: e.cacheKey,
    data: e.data,
    systemPrompt: e.systemPrompt
  }) ?? {
    success: !1,
    error: "empty response",
    content: "",
    canRegenerate: !1
  };
}
function Zt(t) {
  const e = t.includes("?") ? t.split("?")[1] : t, n = new URLSearchParams(e), s = [], r = [];
  return n.getAll("fq").forEach((o) => s.push(o)), n.getAll("fq[]").forEach((o) => s.push(o)), n.getAll("tr").forEach((o) => r.push(o)), n.getAll("tr[]").forEach((o) => r.push(o)), {
    q: n.get("q") || "*",
    p: n.get("p") || "1",
    _setlocale: n.get("_setlocale") || void 0,
    sort: n.get("sort") || void 0,
    fq: s.length ? s : void 0,
    tr: r.length ? r : void 0,
    nfpr: n.get("nfpr") || void 0,
    group: n.get("group") || void 0,
    rows: n.get("rows") || void 0
  };
}
function Te(t) {
  return {
    fetchSearch: (e, n) => ot(t, e, n),
    postClick: (e, n) => At(t, e, n),
    fetchChat: (e, n) => at(t, e, n),
    fetchChatEnabled: (e) => it(t, e),
    postChatConversation: (e, n, s, r) => ct(t, e, n, s, r),
    postAgentChat: (e, n, s, r) => lt(t, e, n, s, r),
    postLlmChat: (e, n, s) => xt(t, e, n, s),
    postSemanticChat: (e, n, s) => Lt(t, e, n, s),
    deleteSiteConversationState: (e, n) => ut(t, e, n),
    deleteAgentFlowState: (e, n, s) => ft(t, e, n, s),
    fetchIntents: (e) => Nt(t, e),
    fetchSiteChatSlots: (e, n) => dt(t, e, n),
    fetchAgentChatSlots: (e, n) => ht(t, e, n),
    postSiteChatSlot: (e, n, s, r) => Ft(t, e, n, s, r),
    postSiteSlotExtract: (e, n, s, r) => _t(t, e, n, s, r),
    fetchSiteChatState: (e, n) => qt(t, e, n),
    postSiteHandoff: (e, n) => Ut(t, e, n),
    postSiteFlowSelect: (e, n, s) => Mt(t, e, n, s),
    fetchLlmInstances: () => Pt(t),
    fetchLlmContextInfo: (e) => Dt(t, e),
    fetchAgentContextInfo: (e, n) => jt(t, e, n),
    fetchAutoComplete: (e, n) => St(t, e, n),
    fetchSortOptions: (e) => Jt(t, e),
    fetchSimilar: (e, n) => pt(t, e, n),
    postSiteSlotUpload: (e, n, s, r, o) => Gt(t, e, n, s, r, o),
    postSiteChatResume: (e, n, s, r) => Ht(t, e, n, s, r),
    fetchSpellCheck: (e, n, s) => Bt(t, e, n, s),
    dslSearch: (e, n, s) => Kt(t, e, n, s),
    dslQuery: (e, n, s) => zt(t, e, n, s),
    fetchDiscovery: () => Vt(t),
    fetchFeatures: () => Xt(t),
    fetchSystemLocales: () => Wt(t),
    fetchLlmVendors: () => Qt(t),
    postSummary: (e) => Yt(t, e)
  };
}
var G = "TUR_SESSION", te = 2592e3;
function ee(t) {
  return t.replaceAll(/[$()*+./?[\\\]^{|}]/g, "\\$&");
}
function gt(t) {
  if (typeof document > "u") return null;
  const e = new RegExp(`(?:^|;\\s*)${ee(t)}=([^;]+)`).exec(document.cookie);
  return e ? decodeURIComponent(e[1]) : null;
}
function Q(t, e, n) {
  if (typeof document > "u") return;
  const s = [
    `${t}=${encodeURIComponent(e)}`,
    "Path=/",
    `Max-Age=${Math.max(0, Math.floor(n))}`,
    "SameSite=None",
    "Secure"
  ];
  document.cookie = s.join("; ");
}
function ne() {
  const t = globalThis.crypto;
  if (t && typeof t.randomUUID == "function") return t.randomUUID();
  const e = (s) => {
    let r = "";
    for (; r.length < s; ) r += Math.random().toString(16).slice(2);
    return r.slice(0, s);
  }, n = (8 + Math.floor(Math.random() * 4)).toString(16);
  return `${e(8)}-${e(4)}-4${e(3)}-${n}${e(3)}-${e(12)}`;
}
function se(t = {}) {
  if (typeof document > "u") return null;
  const e = t.name ?? "TUR_SESSION", n = t.ttlSeconds ?? 2592e3, s = gt(e);
  if (s)
    return Q(e, s, n), s;
  const r = ne();
  return Q(e, r, n), r;
}
function Y(t = G) {
  return gt(t);
}
function $e(t = G) {
  typeof document > "u" || (document.cookie = `${t}=; Path=/; Max-Age=0; SameSite=None; Secure`);
}
function re(t) {
  const e = t.queryContext.defaultFields;
  return (t.results?.document ?? t.groups?.flatMap((n) => n.results?.document ?? []) ?? []).map((n) => ({
    url: n.fields[e.url] ?? "",
    title: n.fields[e.title] ?? "",
    description: n.fields[e.description] ?? "",
    date: n.fields[e.date] ?? "",
    image: n.fields[e.image] ?? "",
    text: n.fields[e.text] ?? "",
    raw: n
  }));
}
function oe(t) {
  if (!t.groups) return [];
  const e = t.queryContext.defaultFields;
  return t.groups.map((n) => ({
    name: n.name,
    count: n.count,
    page: n.page,
    pageCount: n.pageCount,
    limit: n.limit,
    pagination: n.pagination ?? [],
    documents: (n.results?.document ?? []).map((s) => ({
      url: s.fields[e.url] ?? "",
      title: s.fields[e.title] ?? "",
      description: s.fields[e.description] ?? "",
      date: s.fields[e.date] ?? "",
      image: s.fields[e.image] ?? "",
      text: s.fields[e.text] ?? "",
      raw: s
    }))
  }));
}
var K = /^(?:\s*sandbox:\/*)+/i;
function mt(t) {
  try {
    return new URL(t).origin;
  } catch {
    return t.replace(/\/+$/, "");
  }
}
function Ae(t, e = "") {
  if (!t) return t;
  let n = t.trim();
  if (K.test(n)) n = n.replace(K, "/");
  else if (!n.startsWith("/")) return n;
  return e && n.startsWith("/") ? mt(e) + n : n;
}
function Ee(t) {
  return K.test(t ?? "");
}
var ae = /\]\(\s*(?:sandbox:\/*)?(\/api\/[^\s)]+)\)/gi;
function Oe(t, e = "") {
  if (!t || !e) return t;
  const n = mt(e);
  return t.replace(ae, (s, r) => `](${n}${r})`);
}
var ie = /```(html|d2)\r?\n([\s\S]*?)```/g;
function ke(t) {
  if (!t) return [];
  const e = [];
  let n = 0;
  for (const r of t.matchAll(ie)) {
    const o = r.index ?? 0, a = t.slice(n, o);
    a && e.push({
      type: "markdown",
      text: a
    });
    const c = r[1];
    e.push({
      type: c,
      code: r[2].replace(/\r?\n$/, "")
    }), n = o + r[0].length;
  }
  const s = t.slice(n);
  return s && e.push({
    type: "markdown",
    text: s
  }), e;
}
function Re(t) {
  return /```html\r?\n[\s\S]*?```/.test(t ?? "");
}
var U = /* @__PURE__ */ new Map();
function ce(t, e, n, s) {
  return `${t}|${e}|${n}|${s ? "delta" : "snapshot"}`;
}
function le(t, e, n, s, r) {
  const o = `${e}/sn/${n}/chat/${r ? "slots/stream/delta" : "slots/stream"}?conversationId=${encodeURIComponent(s)}`, a = new globalThis.EventSource(o, { withCredentials: !0 }), c = {
    eventSource: a,
    latestSnapshot: null,
    runningSlots: r ? {} : null,
    subscribers: /* @__PURE__ */ new Set()
  }, i = (l) => {
    c.latestSnapshot = l;
    const u = Array.from(c.subscribers);
    for (const p of u) try {
      p.onMessage(l);
    } catch {
    }
  };
  return a.onmessage = (l) => {
    let u;
    try {
      u = JSON.parse(l.data);
    } catch {
      return;
    }
    i(r ? ue(c, u, s) : u);
  }, a.onerror = () => {
    if (a.readyState !== globalThis.EventSource.CLOSED) return;
    const l = Array.from(c.subscribers);
    c.subscribers.clear(), U.delete(t);
    for (const u of l) try {
      u.onClosed();
    } catch {
    }
  }, U.set(t, c), c;
}
function ue(t, e, n) {
  let s = t.runningSlots;
  (s === null || e.snapshot) && (s = {});
  for (const [r, o] of Object.entries(e.added ?? {})) s[r] = o;
  for (const [r, o] of Object.entries(e.updated ?? {})) s[r] = o;
  for (const r of e.removed ?? []) delete s[r];
  return t.runningSlots = s, {
    conversationId: e.conversationId || n,
    slots: { ...s }
  };
}
function fe(t, e, n, s, r) {
  const o = r?.delta === !0, a = ce(t, e, n, o);
  let c = U.get(a);
  if (c === void 0 && (c = le(a, t, e, n, o)), c.subscribers.add(s), c.latestSnapshot !== null) {
    const i = c, l = c.latestSnapshot;
    globalThis.queueMicrotask(() => {
      if (i.subscribers.has(s))
        try {
          s.onMessage(l);
        } catch {
        }
    });
  }
  return { close() {
    const i = U.get(a);
    i !== void 0 && (i.subscribers.delete(s), i.subscribers.size === 0 && (i.eventSource.close(), U.delete(a)));
  } };
}
var q = /* @__PURE__ */ new Map();
function de(t, e, n) {
  return `${t}|${e.site ?? ""}|${e.agentId ?? ""}|${n}`;
}
function he(t, e, n) {
  const s = `conversationId=${encodeURIComponent(n)}`;
  if (e.site) {
    let r = `${t}/sn/${e.site}/chat/workspace/stream?${s}`;
    return e.agentId && (r += `&agentId=${encodeURIComponent(e.agentId)}`), r;
  }
  if (e.agentId) return `${t}/v2/ai-agent/${encodeURIComponent(e.agentId)}/workspace/stream?${s}`;
  throw new Error("subscribeWorkspaceSse: scope must include `site` or `agentId`.");
}
function Se(t) {
  const e = Array.from(t.artifacts.values()).sort((n, s) => n.key.localeCompare(s.key));
  return Object.freeze({
    conversationId: t.conversationId,
    artifacts: e
  });
}
function pe(t, e, n, s) {
  const r = he(e, n, s), o = new globalThis.EventSource(r, { withCredentials: !0 }), a = {
    eventSource: o,
    latestSnapshot: null,
    artifacts: /* @__PURE__ */ new Map(),
    conversationId: s,
    subscribers: /* @__PURE__ */ new Set()
  }, c = () => {
    const i = Se(a);
    a.latestSnapshot = i;
    for (const l of Array.from(a.subscribers)) try {
      l.onMessage(i);
    } catch {
    }
  };
  return o.onmessage = (i) => {
    let l;
    try {
      l = JSON.parse(i.data);
    } catch {
      return;
    }
    !l || typeof l.key != "string" || (l.event === "delete" ? a.artifacts.delete(l.key) : a.artifacts.set(l.key, {
      key: l.key,
      contentType: l.contentType ?? null,
      size: l.size ?? 0,
      signedUrl: l.signedUrl ?? null
    }), c());
  }, o.onerror = () => {
    if (o.readyState !== globalThis.EventSource.CLOSED) return;
    const i = Array.from(a.subscribers);
    a.subscribers.clear(), q.delete(t);
    for (const l of i) try {
      l.onClosed();
    } catch {
    }
  }, q.set(t, a), a;
}
function xe(t, e, n, s) {
  const r = de(t, e, n);
  let o = q.get(r);
  if (o === void 0 && (o = pe(r, t, e, n)), o.subscribers.add(s), o.latestSnapshot !== null) {
    const a = o, c = o.latestSnapshot;
    globalThis.queueMicrotask(() => {
      if (a.subscribers.has(s))
        try {
          s.onMessage(c);
        } catch {
        }
    });
  }
  return { close() {
    const a = q.get(r);
    a !== void 0 && (a.subscribers.delete(s), a.subscribers.size === 0 && (a.eventSource.close(), q.delete(r)));
  } };
}
function Le() {
  return q.size;
}
function M(t) {
  let e = t;
  const n = /* @__PURE__ */ new Set();
  return {
    getState: () => e,
    setState(s) {
      const r = typeof s == "function" ? s(e) : s;
      e = {
        ...e,
        ...r
      };
      for (const o of Array.from(n)) try {
        o(e);
      } catch {
      }
    },
    subscribe(s) {
      return n.add(s), () => {
        n.delete(s);
      };
    }
  };
}
function Ne(t, e, n) {
  const s = M({
    status: "idle",
    data: null,
    chat: null,
    documents: [],
    groups: [],
    error: null,
    params: {
      q: "*",
      p: "1",
      _setlocale: e.locale,
      sort: e.sort ?? "relevance",
      ...n
    }
  });
  let r = 0;
  async function o(a) {
    const c = ++r;
    s.setState({
      status: "loading",
      error: null,
      params: a
    });
    try {
      const i = a.q || "*", l = i !== "*", [u, p] = await Promise.all([ot(t, e.site, a), l ? at(t, e.site, {
        q: i,
        _setlocale: a._setlocale
      }) : Promise.resolve(null)]);
      if (c !== r) return;
      s.setState({
        data: u,
        chat: p,
        documents: re(u),
        groups: oe(u),
        status: "success"
      });
    } catch (i) {
      if (c !== r) return;
      s.setState({
        error: i instanceof Error ? i.message : "Search failed",
        status: "error"
      });
    }
  }
  return {
    getState: s.getState,
    setState: s.setState,
    subscribe: s.subscribe,
    search: (a) => o(a),
    searchQuery: (a) => o({
      ...s.getState().params,
      q: a,
      p: "1"
    }),
    navigate: (a) => o(Zt(a)),
    changeLocale: (a) => o({
      ...s.getState().params,
      _setlocale: a
    }),
    changeSort: (a) => o({
      ...s.getState().params,
      sort: a
    }),
    goToPage: (a) => o({
      ...s.getState().params,
      p: String(a)
    })
  };
}
var ge = 50;
function B() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}
var me = "_ab_variant";
function ye() {
  try {
    const t = globalThis.location?.search;
    return t && new URLSearchParams(t).get(me)?.trim() || void 0;
  } catch {
    return;
  }
}
function be(t) {
  return typeof DOMException < "u" && t instanceof DOMException && t.name === "AbortError" ? !0 : t instanceof Error && t.name === "AbortError";
}
function Z(t, e, n) {
  const s = t.findIndex((o) => o.id === e);
  if (s === -1) return t;
  const r = [...t];
  return r[s] = n(r[s]), r;
}
function ve(t, e) {
  return `turing.chat.${t}.${typeof e == "string" ? e : "default"}`;
}
function tt(t) {
  return typeof t == "object" && t !== null && typeof t.id == "string" && typeof t.content == "string" && (t.role === "user" || t.role === "assistant");
}
function Ie(t) {
  try {
    const e = globalThis.sessionStorage?.getItem(t);
    if (!e) return {
      conversationId: null,
      messages: []
    };
    const n = JSON.parse(e);
    if (Array.isArray(n)) return {
      conversationId: null,
      messages: n.filter(tt)
    };
    if (typeof n == "object" && n !== null) {
      const s = n, r = Array.isArray(s.messages) ? s.messages.filter(tt) : [];
      return {
        conversationId: typeof s.conversationId == "string" && s.conversationId ? s.conversationId : null,
        messages: r
      };
    }
    return {
      conversationId: null,
      messages: []
    };
  } catch {
    return {
      conversationId: null,
      messages: []
    };
  }
}
function et(t, e) {
  try {
    globalThis.sessionStorage?.setItem(t, JSON.stringify(e));
  } catch {
  }
}
function Fe(t, e = {}) {
  const { site: n = "", locale: s, persist: r = !1, maxMessages: o = ge, flowId: a, agent: c, conversationId: i, initialMessages: l } = e, u = e.forcedVariant ?? (e.readAbVariantFromUrl === !1 ? void 0 : ye()), p = c?.id, S = c?.llmInstanceId, m = !!(p && S);
  if (!n && !m) throw new Error("createChatController in site mode requires `options.site`. Either pass it or pass `options.agent` to use agent mode.");
  const g = r && n ? ve(n, r) : null, h = g ? Ie(g) : {
    conversationId: i ?? null,
    messages: l ?? []
  }, f = M({
    enabled: m ? !0 : null,
    disabledReason: null,
    messages: h.messages,
    status: "idle",
    error: null,
    isStreaming: !1,
    conversationId: h.conversationId,
    activeForm: null
  });
  let R = G, P = te, T = 0, A = !1, $ = null, C = h.messages, x = h.conversationId;
  function L(d, y) {
    C = d, g && et(g, {
      conversationId: x,
      messages: d
    }), f.setState(y ? {
      messages: d,
      status: y,
      isStreaming: y === "loading"
    } : { messages: d });
  }
  function b(d) {
    x = d, g && et(g, {
      conversationId: d,
      messages: C
    }), f.setState({ conversationId: d });
  }
  m || !n ? f.setState({ enabled: !0 }) : it(t, n).then((d) => {
    const y = !!d?.enabled;
    d?.sessionCookieName && (R = d.sessionCookieName), typeof d?.sessionTtlSeconds == "number" && d.sessionTtlSeconds > 0 && (P = d.sessionTtlSeconds), f.setState({
      enabled: y,
      disabledReason: y ? null : d?.reason ?? null
    });
  }).catch(() => {
    f.setState({
      enabled: !1,
      disabledReason: null
    });
  });
  function v(d, y) {
    if (!(y !== T || A)) {
      if (be(d)) {
        f.setState({
          status: "idle",
          isStreaming: !1
        });
        return;
      }
      f.setState({
        error: d instanceof Error ? d.message : "Chat request failed",
        status: "error",
        isStreaming: !1
      });
    }
  }
  async function yt(d, y) {
    const O = d.trim();
    if (!O) return;
    const _ = y?.llmInstanceId && p ? y.llmInstanceId : S, N = {
      id: B(),
      role: "user",
      content: O,
      timestamp: Date.now()
    }, E = ++T;
    A = !1, $?.abort();
    const F = new AbortController();
    $ = F, f.setState({
      error: null,
      status: "loading",
      isStreaming: !0
    });
    const k = [...C, N], I = k.length > o ? k.slice(-o) : k;
    L(I);
    const w = i ?? se({
      name: R,
      ttlSeconds: P
    }) ?? x ?? B();
    w !== x && b(w), await z(I.map((D) => ({
      role: D.role,
      content: D.content
    })), E, F, w, _);
  }
  async function z(d, y, O, _, N) {
    try {
      const E = B(), F = {
        id: E,
        role: "assistant",
        content: "",
        timestamp: Date.now()
      }, k = [...C, F];
      L(k.length > o ? k.slice(-o) : k);
      const I = (j) => {
        y !== T || A || L(Z(C, E, (X) => ({
          ...X,
          content: X.content + j
        })));
      }, w = p && N ? await lt(t, p, N, d, {
        conversationId: _,
        flowId: a,
        forcedVariant: u,
        onToken: I,
        signal: O.signal
      }) : await ct(t, n, d, s, {
        conversationId: _,
        flowId: a,
        forcedVariant: u,
        onToken: I,
        signal: O.signal
      });
      if (y !== T || A) return;
      const D = w?.options && w.options.length > 0 ? w.options : void 0, V = w?.form && w.form.fields?.length > 0 ? w.form : void 0, Tt = w?.sources && w.sources.length > 0 ? w.sources : void 0;
      L(Z(C, E, (j) => ({
        ...j,
        content: w?.content ?? j.content,
        options: D,
        form: V,
        sources: Tt
      })), "success"), f.setState({ activeForm: V ?? null });
    } catch (E) {
      v(E, y);
    }
  }
  async function bt(d, y) {
    if (!n)
      return f.setState({
        error: "submitForm requires site mode (the form-submit endpoint is site-scoped)",
        status: "error",
        isStreaming: !1
      }), null;
    const O = x;
    if (!O) return null;
    const _ = f.getState().activeForm, N = ++T;
    A = !1, $?.abort();
    const E = new AbortController();
    $ = E, f.setState({
      error: null,
      status: "loading",
      isStreaming: !0
    }), f.setState({ activeForm: null }), L(C.map((I) => I.form ? {
      ...I,
      form: void 0
    } : I));
    let F = null;
    try {
      F = await Rt(t, n, O, d, _?.nodeId);
    } catch (I) {
      return v(I, N), null;
    }
    if (N !== T || A) return F;
    const k = y?.llmInstanceId && p ? y.llmInstanceId : S;
    return await z(C.map((I) => ({
      role: I.role,
      content: I.content
    })), N, E, O, k), F;
  }
  function vt() {
    A = !0, T++, $?.abort(), $ = null, f.setState({
      status: "idle",
      isStreaming: !1
    });
  }
  function It() {
    A = !0, T++, $?.abort(), $ = null, L([]), f.setState({
      error: null,
      status: "idle",
      isStreaming: !1,
      activeForm: null
    });
  }
  async function wt() {
    const d = x;
    if (!d) return !1;
    try {
      return p && S ? a ? await ft(t, p, a, d) : !1 : n ? await ut(t, n, d) > 0 : !1;
    } catch {
      return !1;
    }
  }
  function Ct() {
    A = !0, T++, $?.abort(), $ = null;
  }
  return {
    getState: f.getState,
    setState: f.setState,
    subscribe: f.subscribe,
    send: yt,
    submitForm: bt,
    stop: vt,
    reset: It,
    resetFlow: wt,
    destroy: Ct
  };
}
function _e(t, e, n = 300) {
  const s = M({
    suggestions: [],
    isLoading: !1
  });
  let r, o = 0;
  function a(i, l) {
    if (r && clearTimeout(r), !i || i.length < 2) {
      s.setState({
        suggestions: [],
        isLoading: !1
      });
      return;
    }
    s.setState({ isLoading: !0 }), r = setTimeout(async () => {
      const u = ++o;
      try {
        const p = await St(t, e.site, {
          q: i,
          _setlocale: e.locale,
          sort: e.sort ?? "relevance",
          ...l
        });
        u === o && s.setState({
          suggestions: p,
          isLoading: !1
        });
      } catch {
        u === o && s.setState({
          suggestions: [],
          isLoading: !1
        });
      }
    }, n);
  }
  function c() {
    r && clearTimeout(r), o++, s.setState({
      suggestions: [],
      isLoading: !1
    });
  }
  return {
    getState: s.getState,
    setState: s.setState,
    subscribe: s.subscribe,
    fetch: a,
    clear: c
  };
}
var nt = Object.freeze({});
function qe(t, e, n = {}) {
  const { conversationId: s, sessionCookieName: r = G, pollInterval: o = 0, enabled: a = !0, transport: c = "polling", sseDelta: i = !1 } = n, { site: l, agentId: u } = e;
  if (!l && !u) throw new Error("createSlotsController requires `scope.site` or `scope.agentId`.");
  const p = c === "sse" && !u && typeof globalThis.EventSource < "u", S = M({
    slots: nt,
    conversationId: s ?? Y(r),
    status: "idle",
    error: null
  });
  let m = null, g = !1, h = null, f, R = !1;
  function P() {
    return s ?? Y(r);
  }
  function T(b, v) {
    m = v, S.setState({
      slots: Object.freeze({ ...b?.slots ?? {} }),
      status: "success"
    });
  }
  function A(b) {
    S.setState({
      status: "loading",
      error: null
    }), (u ? ht(t, u, b) : dt(t, l, b)).then((v) => {
      R || S.getState().conversationId !== b || T(v, b);
    }).catch((v) => {
      R || S.getState().conversationId !== b || S.setState({
        error: v instanceof Error ? v.message : "Failed to load chat slots",
        status: "error"
      });
    });
  }
  function $(b) {
    S.setState({
      status: "loading",
      error: null
    }), h = fe(t.baseURL, l, b, {
      onMessage: (v) => {
        R || T(v, b);
      },
      onClosed: () => {
        g = !0, h = null, S.setState({ error: "SSE channel closed; falling back to polling." }), C();
      }
    }, { delta: i });
  }
  function C() {
    if (R || !a) return;
    const b = P();
    b !== S.getState().conversationId && (m && m !== b && (m = null, S.setState({ slots: nt })), h?.close(), h = null, S.setState({ conversationId: b }));
    const v = S.getState().conversationId;
    if (v) {
      if (p && !g) {
        h || $(v);
        return;
      }
      A(v);
    }
  }
  a && o > 0 && (f = globalThis.setInterval(() => {
    p && !g && S.getState().conversationId || C();
  }, o)), C();
  function x() {
    C();
  }
  function L() {
    R = !0, f && globalThis.clearInterval(f), h?.close(), h = null;
  }
  return {
    getState: S.getState,
    setState: S.setState,
    subscribe: S.subscribe,
    refresh: x,
    destroy: L
  };
}
function Ue(t, e, n = {}) {
  const s = M({
    status: "idle",
    seedId: null,
    results: [],
    error: null
  });
  let r = 0;
  async function o(c, i) {
    const l = ++r;
    s.setState({
      status: "loading",
      seedId: c,
      error: null
    });
    try {
      const u = await pt(t, e, {
        id: c,
        rows: i?.rows ?? n.rows,
        locale: i?.locale ?? n.locale,
        mode: i?.mode ?? n.mode
      });
      if (l !== r) return;
      s.setState({
        results: u,
        status: "success"
      });
    } catch (u) {
      if (l !== r) return;
      s.setState({
        error: u instanceof Error ? u.message : "Similar lookup failed",
        status: "error"
      });
    }
  }
  function a() {
    r++, s.setState({
      status: "idle",
      seedId: null,
      results: [],
      error: null
    });
  }
  return {
    getState: s.getState,
    setState: s.setState,
    subscribe: s.subscribe,
    load: o,
    clear: a
  };
}
export {
  Ce as CHAT_DISABLED_HINTS,
  G as TUR_SESSION_DEFAULT_COOKIE_NAME,
  te as TUR_SESSION_DEFAULT_TTL_SECONDS,
  Le as _workspaceSseOpenChannelCount,
  Oe as absolutizeArtifactUrls,
  $e as clearTurSession,
  _e as createAutoComplete,
  Fe as createChatController,
  Ne as createSearchController,
  Ue as createSimilarController,
  qe as createSlotsController,
  M as createStore,
  Te as createTuringApi,
  we as createTuringClient,
  ft as deleteAgentFlowState,
  ut as deleteSiteConversationState,
  zt as dslQuery,
  Kt as dslSearch,
  ht as fetchAgentChatSlots,
  jt as fetchAgentContextInfo,
  St as fetchAutoComplete,
  at as fetchChat,
  it as fetchChatEnabled,
  Vt as fetchDiscovery,
  Xt as fetchFeatures,
  Nt as fetchIntents,
  Dt as fetchLlmContextInfo,
  Pt as fetchLlmInstances,
  Qt as fetchLlmVendors,
  ot as fetchSearch,
  pt as fetchSimilar,
  dt as fetchSiteChatSlots,
  qt as fetchSiteChatState,
  Jt as fetchSortOptions,
  Bt as fetchSpellCheck,
  Wt as fetchSystemLocales,
  se as getOrCreateTurSession,
  Re as hasHtmlBlock,
  Ee as isSandboxUrl,
  Zt as parseHrefToParams,
  lt as postAgentChat,
  ct as postChatConversation,
  At as postClick,
  xt as postLlmChat,
  Lt as postSemanticChat,
  Ht as postSiteChatResume,
  Ft as postSiteChatSlot,
  Mt as postSiteFlowSelect,
  Rt as postSiteFormSubmit,
  Ut as postSiteHandoff,
  _t as postSiteSlotExtract,
  Gt as postSiteSlotUpload,
  Yt as postSummary,
  Y as readTurSession,
  re as resolveDocuments,
  oe as resolveGroups,
  Ae as resolveSandboxUrl,
  ke as splitRichContent,
  fe as subscribeSlotsSse,
  xe as subscribeWorkspaceSse
};

//# sourceMappingURL=turing-sdk.js.map