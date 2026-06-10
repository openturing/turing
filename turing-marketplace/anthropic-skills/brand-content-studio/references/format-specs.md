# Format Specifications

Authoritative per-format rules. Find the section for the format chosen in
step 1 of the core workflow.

## article

- **Required sections (beats):** Hook · 3–5 body beats · Call to Action.
- **Target length:** 800–1200 words.
- **Tone override:** none (use the global voice).
- **Notes:** Each body beat gets a `##` heading. Include at most one pull quote
  (use `pick_quote.py`). End with a single CTA link or action.

## video-script

- **Required sections:** Cold open · body beats with `[B-roll]` cues · Outro.
- **Target length:** 90–180 seconds of read time (~150 wpm → 225–450 words).
- **Tone override:** more spoken, shorter sentences, second person.
- **Notes:** Every beat must carry at least one `[B-roll: …]` or `[on screen: …]`
  cue on its own line. The Cold open is the Hook beat; the Outro is the CTA beat.

## newsletter

- **Required sections:** Subject line · Hook · 2–3 body beats · Subscribe CTA.
- **Target length:** 250–450 words (excluding subject line).
- **Tone override:** warmer, first person plural ("we") allowed.
- **Notes:** The subject line is ≤ 60 characters and must not use forbidden
  vocabulary. The Subscribe CTA is mandatory and always last.

## one-pager

- **Required sections:** Headline · Problem · Solution · Proof · Call to Action.
- **Target length:** ≤ 1 page (~450 words).
- **Tone override:** STRATEGIC register by default (see brand-voice.md).
- **Notes:** "Proof" must contain at least one concrete number or named
  customer. No pull quotes. Fits on one printed page.

## Length enforcement

If a draft is over the upper bound, cut a body beat rather than trimming every
beat evenly — losing a whole idea reads better than thinning all of them.
