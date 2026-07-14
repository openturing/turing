# AI Personas — Give Your AI a Brand Voice

> Your customer doesn't want to talk to an LLM. They want to talk to your
> company. A Persona is the voice profile of your AI in Viglet Turing ES: define
> it once, attach it to any AI Agent, and every reply sounds like your most
> senior, most on-brand representative. Swap the LLM, swap the tools — the voice
> stays. Self-hosted, open source (Apache 2.0).

Page: https://turing.viglet.org/persona

## What is an AI persona?

A persona is the voice profile of your AI — a small, reusable bundle of decisions
(system instruction, tone, verbosity, required and forbidden vocabulary, optional
Big Five personality and knowledge grounding) that replaces an LLM's generic
default voice with your company's. You configure it once in Administration →
Personas and attach it to any AI Agent; the same persona keeps every agent
sounding like the same representative. The voice is decoupled from the model and
the tools — change the LLM and the persona stays.

## How a persona reaches production (4 steps)

1. **Define the voice once** — describe who the persona is (a free-text system
   instruction), set tone, verbosity and language style, and pin the vocabulary
   you require and forbid. An AI-authoring assistant can draft every field from a
   plain-language brief; nothing is saved until you approve it.
2. **Attach it to any agent** — give an agent a catalog of personas and star one
   as default. A chat flow can switch the active persona mid-conversation.
3. **Govern in two layers** — forbidden terms are enforced in the prompt (the
   model never intends to say them) and again after the response, where any slip
   is masked as `[***]`. It works in Portuguese, English and Spanish and matches
   word variants and whole phrases.
4. **Measure the audience** — turn a persona into a reader profile and score
   whether content fits the people meant to read it (readability + a
   text-grounded AI review). Persona Match scales this across many contents ×
   many audiences on a schedule.

## Why not just write a good prompt?

| Capability | Turing Persona | Prompt engineering | Off-the-shelf chatbot |
| --- | --- | --- | --- |
| One brand voice, reused across every agent | Yes | Copy-paste | Partial |
| Mandatory & forbidden vocabulary, enforced | Two layers | No | No |
| Post-response tone masking (PT · EN · ES) | Yes | No | No |
| Teach the voice by example (few-shot store) | Yes | Manual | No |
| Live brand facts via MCP — no redeploy | Yes | No | No |
| Ground answers in your indexed content | Yes | Manual | No |
| Big Five (OCEAN) personality control | Yes | No | No |
| Swap the LLM — the voice stays | Yes | Rewrite | No |
| Audience personas + content-fit scoring | Yes | No | No |
| Persona Match — N×N content × audience | Yes | No | No |
| Synthetic user research at scale | Yes | No | No |
| Draft a persona from a voice recording | Yes | No | No |
| Configured by non-engineers, no code | Yes | No | Partial |

## Far more than a system prompt

- **A voice for every agent** — speaks in your tone, uses your mandatory
  vocabulary, never the forbidden terms. Talk to a persona directly on a
  shareable URL to approve the voice before it goes live.
- **Audiences & content-fit** — model a target reader (reading level, domain
  expertise, vocabulary ceiling) and score whether a page fits them, with the
  exact spans that miss and rewrite suggestions.
- **Persona Match (N×N)** — a reusable project scoring many contents against many
  audiences at once, on a daily/weekly schedule, with a live heatmap and PDF
  export.
- **Persona Dialogue** — put two or more brand voices in a room and let them
  debate a topic, live — a "diff of voices" before you decide which to attach.
- **Synthetic user research** — interview a cohort of personas against a research
  script and summarize by theme, with a saturation signal. A discovery aid, never
  a substitute for real users.
- **Draft from a recording** — transcribe five minutes of "here's how our ideal
  rep sounds" into a persona draft to review and save.

## Live demo

A live content-fit demo runs on https://turing.viglet.org/persona — pick an
audience persona, paste text, and get a fit verdict (readability + text-grounded
AI review) against the demo backend.

## The Persona Book

The complete, didactic, strategic guide — with a worked example for every
feature and six customer scenarios (e-commerce, banking, healthcare, SaaS, public
sector, media) — is "The Persona Book": https://docs.viglet.org/turing/persona-book

## Links

- Personas page: https://turing.viglet.org/persona
- The Persona Book: https://docs.viglet.org/turing/persona-book
- Personas reference: https://docs.viglet.org/turing/personas
- Documentation: https://docs.viglet.org/turing/
