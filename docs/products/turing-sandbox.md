# Sub-Product Brief — Turing Sandbox

> **One line:** a managed, multi-tenant Python sandbox for AI agents and
> developers — a self-serve **Modal / E2B competitor** built on Turing's shipped
> Code Interpreter.
>
> Status: **deferred** (highest upside, worst fit for the solo/no-infra
> constraint — see §6). Ties to T80 (Code Interpreter Docker mode, shipped),
> T239 (sandbox hardening), and STRATEGY.md §VIII.1.

---

## 1. The idea

Expose the Code Interpreter as a standalone, usage-billed cloud: a developer
sends code (or an agent's tool-call sends it), it runs in an isolated container
with resource limits and signed-URL artifact access, and they pay per minute of
execution. No Turing platform adoption required — the sandbox is the product.

## 2. Who buys it

AI/agent developers who need a Python execution sandbox and don't want to build
container isolation themselves. Same buyer as Modal, E2B, Daytona. **Different
buyer from the rest of Turing** — pure usage-based, self-serve, no enterprise
sales motion.

## 3. What's already built (the moat)

This is the unusual case where the hard part exists. Shipped today:

- `TurCodeInterpreterToolService` — the execution entry point.
- `TurCodeInterpreterExecutionMode` — pluggable execution backends (incl. Docker).
- `TurCodeInterpreterResourceLimiter` — CPU/memory/time caps.
- `TurCodeInterpreterWarmPool` — pre-warmed containers for low-latency starts.
- `TurCodeInterpreterUrlSigner` — signed URLs for output artifacts.
- `TurCodeInterpreterCleanupTask` — teardown / GC of spent sandboxes.
- `TurCodeInterpreterFileAPI` — file in/out of the sandbox.

The *differentiator* (warm pool + resource limiting + URL signing) is already
written. What's missing is **productization around it**, not the engine.

## 4. What's new (to build)

- **Isolation hardening** — T239: seccomp + gVisor on top of the existing
  `--cap-drop ALL --security-opt no-new-privileges` + read-only FS. Non-negotiable
  for a public, untrusted-code service.
- **Multi-tenancy** — per-tenant quotas, isolation, and a public API key surface.
- **Metering + billing** — per-minute usage accounting and a payment integration.
- **A self-serve onboarding** — signup → API key → first run.

## 5. Monetization

Pure usage-based: **$0.05/min** of execution (STRATEGY.md §VIII.1), free tier to
seed adoption. No open-core split needed — the hosted runtime *is* the paid thing.

## 6. Why it's deferred (the honest part)

This is the **opposite** of the constraint this folder optimizes for. A public
multi-tenant sandbox means: servers you operate, untrusted code you're liable
for, an on-call rotation, a billing system, and abuse/fraud handling. That is
real infra, real ops, and real risk — exactly what a solo maintainer with no
capital should *not* sign up for.

**What would flip the verdict:**
- A co-founder/partner who owns operations, **or** capital to pay for managed
  infra and on-call.
- A named customer already asking for hosted code execution (don't build it on
  spec).

**The zero-infra fallback that *is* viable solo:** sell the Code Interpreter as a
**self-hosted, licensed add-on** (open-core + offline-signed license, like
[Agent Eval](agent-eval.md) §5) — the customer runs the container on their own
infra; you ship the hardened engine and charge for it. Lower ceiling than the
SaaS, but it fits the constraint and reuses everything in §3 with almost no new
code. Prefer this until §6's flip conditions are met.
