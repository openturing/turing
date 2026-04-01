import { useMemo, type ReactNode } from "react";
import { TuringContext } from "./context";
import type { TuringConfig } from "./types";

/**
 * TuringProvider makes the Turing configuration available to all hooks.
 *
 * @example
 * ```tsx
 * <TuringProvider config={{
 *   endpoint: "http://localhost:2700/api",
 *   site: "my-site",
 *   locale: "en_US",
 *   sort: "relevance",
 * }}>
 *   <App />
 * </TuringProvider>
 * ```
 *
 * @since 2026.2.0
 */
export function TuringProvider({
  config,
  children,
}: {
  config: TuringConfig;
  children: ReactNode;
}) {
  const value = useMemo(() => ({ config }), [config]);

  return (
    <TuringContext.Provider value={value}>
      {children}
    </TuringContext.Provider>
  );
}
