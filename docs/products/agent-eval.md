# Sub-Product Brief — Turing Agent Eval

> **One line:** *"Does your AI agent still work after the model changed?"* — a
> provider-agnostic regression-testing / continuous-assurance tool for AI agents,
> delivered as a CLI + GitHub Action, with Turing's LLM-as-judge as the paid
> differentiator.
>
> Status: **proposed sub-product.** The engine is new code; it *reuses* shipped
> Turing assets (see §6). Related platform tasks: `T125`–`T127` (`turing eval`),
> Block K `T285`–`T288` (Agent CI). This brief is the product/architecture view;
> task status stays in [`../ROADMAP.md`](../ROADMAP.md).

---

## 1. The problem it sells against

The platform installs, works, and then never needs the customer again. That is
why consultancy ends at delivery and there is no recurring revenue. **AI agents,
however, rot:**

- The `gpt-4o` of June ≠ the one of December; prompts that worked silently drift.
- Providers deprecate models; a pinned model disappears.
- An author tweaks a persona or a flow and can't prove they broke nothing.

So the agent is the one thing the customer **cannot install and forget** — it
*must* be re-checked on every model release and every prompt change. That
recurring re-check is the product. It converts a dead "installed, works, bye"
relationship into a subscription.

## 2. Who buys it

- **Primary (now): existing Turing consultancy clients.** They already run a
  Turing instance that works but sits idle. The pitch: *"For €X/mo I guarantee
  your agent keeps working as models change, with a regression report on every
  release."* No new customer acquisition — this attacks the real bottleneck
  (distribution) by selling to people who already know you.
- **Secondary (later): any team shipping an LLM agent**, Turing or not — because
  the CLI core is provider-agnostic. This is the open-source distribution play.

## 3. How it works (developer experience)

```
$ turing-eval run evals/onboarding.eval.yaml --judge https://my-turing/api
  ✓ greeting stays in persona            (judge: 0.94)
  ✓ captures email when given            (slot: email == "a@b.com")
  ✗ refund flow no longer offers human   (tool forbidden: escalate_human FIRED)
  2 passed, 1 failed — regression in refund.flow since prompt change

# in CI (runs on the CUSTOMER's GitHub Actions runner — zero infra for us):
- uses: openviglet/agent-eval-action@v1
  with: { suite: evals/, judge-url: ${{ secrets.TURING_URL }} }
```

The eval spec (YAML, per `T126`) lists conversations + per-turn assertions:
deterministic ones (slot value, regex, tool-call expected/forbidden) **and**
semantic ones (persona invariant, answer correctness) judged by the LLM.

## 4. Architecture — what lives where

The governing rule for a **solo maintainer**: share a *sharp, small surface*;
never drag the monolith into the CLI repo, and **never duplicate the judge**.

```
┌─────────────────────────────────────┐        ┌──────────────────────────────┐
│  agent-eval  (NEW, standalone repo)  │        │  turing  (existing monolith) │
│                                      │        │                              │
│  • YAML spec parser (T126)           │        │  TurLlmSummaryService  ◀─┐   │
│  • conversation executor             │        │  LlmJudgeGuardrailStrategy│  │
│  • deterministic assertions          │        │  (the differentiator)     │  │
│      slot == / regex / tool-call     │        │                           │  │
│  • reporter (CLI + JUnit XML + PR)    │       │  POST /api/v2/summary  ───┘   │
│  • offline license validation        │        │   (or new /api/v2/eval/judge)│
│  • transport: turing-java-sdk ───────┼──HTTP──▶  reused, like Dumont does    │
│      (only when agent == Turing)     │        │                              │
│  • judge client  ────────────────────┼──HTTP──▶  judge verdict               │
└─────────────────────────────────────┘        └──────────────────────────────┘
        Apache-2.0 open core                       judge stays here = no fork
```

### Share / don't-share matrix

| Share (deliberately) | NEVER pull into the eval repo |
|---|---|
| **The judge** — via HTTP now, slim lib later | Spring Boot app |
| The YAML eval-spec format (defined *here*, Turing may consume it) | Solr / JPA / entities |
| `turing-java-sdk` (`HttpTurSNServer`) as optional transport | React / frontend |
| | chat-flow engine, storage, slots |

### How the judge is shared — two clean ways, one forbidden

1. **✅ HTTP contract (phase 1, recommended).** The CLI calls a judge endpoint on
   a running Turing instance. **Zero judge code in the new repo.** This is exactly
   the pattern Dumont already uses against `TurSummaryAPI`. Maintenance ≈ zero.
2. **✅ Slim extracted library (phase 2).** Extract `turing-llm-judge` (judge +
   LLM client, **no Spring Boot**) as a JAR that *both* the monolith and the CLI
   depend on — one source of truth. Aligns with the `turing-llm` layer proposed
   in [`../STRATEGY.md`](../STRATEGY.md) §VIII.5. Enables a self-contained CLI for
   customers who don't run Turing (consider GraalVM native-image for fast startup).
3. **❌ Copy-paste the judge.** Two divergent judges, double maintenance. For a
   solo dev this is fatal. Never do it.

### The judge contract (phase 1 — reuses shipped code)

The existing generic endpoint already fits:

```
POST /api/v2/summary           (TurSummaryAPI → TurLlmSummaryService)
  body: SummaryRequest(cacheKey, data, systemPrompt)
  resp: SummaryResult(success, error, content, canRegenerate)
```

For judging: pack the `conversation + criterion` into `data`, put the judge
instruction in `systemPrompt`, and have the CLI parse the verdict from `content`.
**Zero new backend code.** When a structured verdict is wanted, add a thin
sibling that returns `{ verdict, score, rationale }`, built on the *same*
`TurLlmSummaryService` + `LlmJudgeGuardrailStrategy` — still one judge:

```
POST /api/v2/eval/judge        (new, ~1 controller, reuses both services)
  body: { criterion, conversation:[{role,content}...], expected? }
  resp: { verdict: "pass"|"fail", score: 0..1, rationale }
```

## 5. Monetization without infra

Open-core, validated **offline** so there is no license server to run:

| Layer | What | Price model |
|---|---|---|
| **Open-source core** | CLI + YAML spec + deterministic assertions + reporter | free (the distribution bait) |
| **Pro (paid)** | LLM-as-judge, regression diff, PR gate / GitHub Action, versioned golden-sets | per-seat / per-repo, **unlocked by an Ed25519-signed license file validated locally** — no server |
| **Eval packs** | Pre-built golden-sets per vertical (Education, Finance) | one-off digital product (marketplace) |

The Ed25519 trick is the answer to *"I can't do licensing, I have no infra"*: you
sign a license file with your private key; the CLI verifies it with the embedded
public key, fully offline. Zero servers, zero ops.

## 6. Reused (already shipped) vs. new (to build)

**Reuse (exists today):**
- `LlmJudgeGuardrailStrategy`, `TurPersonaToneValidator` — the judge/differentiator.
- `TurLlmSummaryService` + `TurSummaryAPI` (`POST /api/v2/summary`) — the proven
  external-call seam (Dumont already consumes it via `RestClient`).
- `turing-java-sdk` / `HttpTurSNServer` — standalone transport when the agent is Turing.

**Build new (the slim, clean repo):**
- YAML spec parser, conversation executor, deterministic assertions, reporter,
  GitHub Action, offline license validation. None of this touches the monolith.

## 7. MVP shape (realistic for a solo dev, medium-term)

1. New repo `agent-eval`; CLI scaffold + YAML schema (`T126`).
2. Conversation executor + deterministic assertions (no LLM yet) → already useful.
3. Judge client → `POST /api/v2/summary` on the customer's own Turing (phase-1
   HTTP, zero new backend).
4. Reporter: console + JUnit XML; then the GitHub Action wrapper.
5. Pro gate: wire the LLM-judge path behind offline license validation.
6. (Later) extract `turing-llm-judge` for self-contained mode.

Nothing here needs a server of yours. The compute is the customer's CI; the judge
is the customer's Turing instance.

## 8. Why this, and not the alternatives

See the [sub-products matrix](README.md#the-matrix). Agent Eval is the only
candidate that is simultaneously **recurring** (agents rot), **zero-infra**
(runs on customer CI + their judge), and **sellable to the existing base**
(reactivates dormant installs) — the three constraints that actually bind here.
