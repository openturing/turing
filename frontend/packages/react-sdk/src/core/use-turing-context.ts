import { useContext } from "react";
import { TuringContext, type TuringContextValue } from "./context";

export function useTuringContext(): TuringContextValue {
  const ctx = useContext(TuringContext);
  if (!ctx) {
    throw new Error(
      "useTuringContext must be used within a <TuringProvider>."
    );
  }
  return ctx;
}

/**
 * Non-throwing variant of {@link useTuringContext}. Returns {@code null}
 * when no {@code <TuringProvider>} ancestor is mounted. Used by hooks that
 * are valid in agent mode without a site context (e.g. {@link useTuringSlots}
 * with {@code options.agent} set) — those callers don't need
 * {@code config.site} and shouldn't crash when hosted inside an admin
 * console that legitimately has no site.
 *
 * <p>Hooks that need the context should still prefer {@link useTuringContext}
 * — a thrown error catches misuse early. Use this one only when the
 * context is genuinely optional for the hook's operation.
 *
 * @since 2026.3.1
 */
export function useOptionalTuringContext(): TuringContextValue | null {
  return useContext(TuringContext);
}
