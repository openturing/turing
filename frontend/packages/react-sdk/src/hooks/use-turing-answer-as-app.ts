import { useMemo } from "react";
import {
  ANSWER_AS_APP_COMPONENTS,
  type TuringGenerativeRegistry,
} from "@viglet/turing-react-ui";
import { useGenerativeUI, type UseGenerativeUIReturn } from "./use-turing-generative-ui";

/**
 * T442 / §XXIII.1 — answer-as-an-app convenience hook.
 *
 * <p>A thin wrapper over {@link useGenerativeUI} pre-wired with the built-in
 * answer-as-app components ({@code comparison_table} / {@code spec_card} /
 * {@code configurator}) that match the backend's built-in client tools
 * (advertised when the agent has {@code answerAsAppEnabled}). Spread
 * {@link UseGenerativeUIReturn.clientTools} into {@code useTuringChat} and render
 * {@link UseGenerativeUIReturn.items} with {@code TuringGenerativeContent} using
 * {@link UseAnswerAsAppReturn.registry}.
 *
 * <p>Pass {@code extra} to add or override components (e.g. a branded
 * comparison table) — extras win on name collision.
 *
 * <pre>
 *   const app = useAnswerAsApp();
 *   const chat = useTuringChat({ agent, clientTools: app.clientTools });
 *   &lt;TuringGenerativeContent items={app.items} registry={app.registry} onRespond={app.respond} /&gt;
 * </pre>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export interface UseAnswerAsAppReturn extends UseGenerativeUIReturn {
  /** The resolved registry (built-ins merged with any `extra`). */
  registry: TuringGenerativeRegistry;
}

export function useAnswerAsApp(extra?: TuringGenerativeRegistry): UseAnswerAsAppReturn {
  const registry = useMemo<TuringGenerativeRegistry>(
    () => ({ ...ANSWER_AS_APP_COMPONENTS, ...(extra ?? {}) }),
    [extra],
  );
  const gen = useGenerativeUI(registry);
  return { ...gen, registry };
}
