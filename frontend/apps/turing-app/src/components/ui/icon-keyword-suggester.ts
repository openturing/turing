import { useCallback, useEffect, useState } from "react";
import { postLlmChat, type TurChatConversationMessage } from "@viglet/turing-react-sdk";
import { TurGlobalSettingsService } from "@/services/system/global-settings.service";

const globalSettingsService = new TurGlobalSettingsService();

/**
 * T1009 — this product's half of the icon picker's suggestion pass.
 *
 * The design system's `IconPickerDialog` takes a `suggestKeywords` prop and does
 * everything after it: searching Iconify for each keyword, grouping the results,
 * and showing the button only when the prop is supplied. What it deliberately
 * does not know is where keywords come from — that is one product's model, its
 * settings service and its chat endpoint, and a package holding it would put
 * Turing's AI integration into every product's chrome.
 *
 * So this is the seam, and it is the whole reason the local dialog could finally
 * become a shim: the suggestion path was the only thing the package's copy
 * lacked, and taking that copy without wiring this would have made the picker
 * quietly stop suggesting on a live page.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

/** What the model is asked for, and the only shape this parses back. */
const KEYWORD_COUNT = 5;

function buildPrompt(context: string): string {
  return `Given this UI element context: "${context}"

Suggest ${KEYWORD_COUNT} single English keywords (nouns) that best represent this concept as an icon search term.
Return ONLY a JSON array of strings, nothing else. Example: ["briefcase","calendar","target","rocket","chart"]`;
}

/**
 * Pull the keyword list out of a model reply.
 *
 * Read leniently, by locating the first bracketed span rather than parsing the
 * whole reply: a small model will wrap the array in prose or a fence however
 * firmly it is told not to, and the repository's standing note on this is not to
 * rely on a bare model honouring a structured-output instruction.
 */
export function parseKeywords(reply: string): string[] {
  // `[^\]]*` rather than a greedy `[\s\S]*`: a keyword array holds no nested
  // bracket, and the greedy form backtracks super-linearly on a long reply.
  const span = /\[[^\]]*\]/.exec(reply);
  if (!span) return [];
  try {
    const parsed = JSON.parse(span[0]) as unknown;
    if (!Array.isArray(parsed)) return [];
    return parsed
      .filter((s): s is string => typeof s === "string" && s.trim().length > 0)
      .map((s) => s.trim())
      .slice(0, KEYWORD_COUNT);
  } catch {
    return [];
  }
}

/**
 * The `suggestKeywords` function for the design system's icon picker, or
 * `undefined` when this deployment has no default LLM.
 *
 * Returning `undefined` is load-bearing: the package hides its suggest button
 * when the prop is absent, so an install with no model configured shows a plain
 * search dialog rather than a button that cannot work. That mirrors what the
 * local copy did by gating on `defaultLlmId`, without the package having to know
 * what an LLM instance is.
 */
export function useIconKeywordSuggester():
  | ((context: { title?: string; description?: string }) => Promise<string[]>)
  | undefined {
  const [defaultLlmId, setDefaultLlmId] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;
    globalSettingsService
      .query()
      .then((settings) => {
        if (alive) setDefaultLlmId(settings.defaultLlmId ?? null);
      })
      .catch(() => {
        // No settings reachable — no suggestion path. The dialog still searches.
        if (alive) setDefaultLlmId(null);
      });
    return () => {
      alive = false;
    };
  }, []);

  // Declared before the early return below, because a hook cannot be called
  // conditionally. `defaultLlmId` is captured rather than read at call time, so
  // the identity changes exactly when the configured model does.
  const suggest = useCallback(
    async ({ title, description }: { title?: string; description?: string }) => {
      const context = [title, description].filter(Boolean).join(" — ").trim();
      if (!context || !defaultLlmId) return [];
      const messages: TurChatConversationMessage[] = [
        { role: "user", content: buildPrompt(context) },
      ];
      const reply = await postLlmChat(defaultLlmId, messages);
      return parseKeywords(reply.content);
    },
    [defaultLlmId],
  );

  return defaultLlmId ? suggest : undefined;
}
