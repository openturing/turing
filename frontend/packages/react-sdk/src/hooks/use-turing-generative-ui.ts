import { useCallback, useMemo, useRef, useState } from "react";
import type { ClientToolHandler } from "../core/types";
import type {
  TuringGenerativeItem,
  TuringGenerativeRegistry,
} from "@viglet/turing-react-ui";

/**
 * T440 — tool-driven generative UI.
 *
 * <p>Turns a name -> React component registry into client-tool handlers (T439):
 * when the agent calls a tool named after a registered component, the handler
 * renders that component (adding it to {@link UseGenerativeUIReturn.items}) and
 * <em>parks</em> the turn on a promise that resolves when the component calls
 * its {@code respond} — immediately for display-only output, or on user
 * interaction for a picker/configurator. The resolved value flows back to the
 * agent as the tool result and the turn continues. No backend beyond T438.
 *
 * <p>Usage:
 * <pre>
 *   const registry = { price_table: PriceTable, color_picker: ColorPicker };
 *   const gen = useGenerativeUI(registry);
 *   const chat = useTuringChat({ agent, clientTools: gen.clientTools });
 *   // ...render the chat, then:
 *   &lt;TuringGenerativeContent items={gen.items} registry={registry} onRespond={gen.respond} /&gt;
 * </pre>
 *
 * The agent must declare the same tool names as client tools (`clientToolsJson`,
 * `clientToolsEnabled`). Component names map 1:1 to client-tool names.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export interface UseGenerativeUIReturn {
  /** Spread into {@code useTuringChat}'s {@code clientTools} option. */
  clientTools: Record<string, ClientToolHandler>;
  /** Pending generative items to pass to {@code TuringGenerativeContent}. */
  items: TuringGenerativeItem[];
  /** Resolve a rendered component's client-tool call (wire to `onRespond`). */
  respond: (id: string, value: unknown) => void;
  /** Drop all pending items, resolving their parked calls with {@code undefined}. */
  clear: () => void;
}

function generativeId(): string {
  return `gen-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

/**
 * Builds the client-tool handler for one registered component name: it renders
 * the component (pushing an item) and returns a promise the parked turn awaits
 * until the component responds. Extracted to keep nesting shallow.
 */
function makeGenerativeHandler(
  name: string,
  resolvers: Map<string, (value: unknown) => void>,
  setItems: React.Dispatch<React.SetStateAction<TuringGenerativeItem[]>>,
): ClientToolHandler {
  return (args) =>
    new Promise<unknown>((resolve) => {
      const id = generativeId();
      resolvers.set(id, resolve);
      setItems((prev) => [
        ...prev,
        { id, component: name, props: (args ?? {}) as Record<string, unknown> },
      ]);
    });
}

export function useGenerativeUI(registry: TuringGenerativeRegistry): UseGenerativeUIReturn {
  const [items, setItems] = useState<TuringGenerativeItem[]>([]);
  const resolversRef = useRef<Map<string, (value: unknown) => void>>(new Map());

  const respond = useCallback((id: string, value: unknown) => {
    const resolve = resolversRef.current.get(id);
    if (resolve) {
      resolve(value);
      resolversRef.current.delete(id);
    }
    setItems((prev) => prev.filter((item) => item.id !== id));
  }, []);

  const clear = useCallback(() => {
    for (const resolve of resolversRef.current.values()) resolve(undefined);
    resolversRef.current.clear();
    setItems([]);
  }, []);

  // Rebuild the handlers only when the SET of component names changes. Names are
  // tool identifiers (no spaces), so a space separator can never collide. Each
  // handler captures only stable refs (setItems / resolversRef), so the table is
  // referentially stable across renders that don't change the registry's keys.
  const nameKey = Object.keys(registry)
    .sort((a, b) => a.localeCompare(b))
    .join(" ");
  const clientTools = useMemo<Record<string, ClientToolHandler>>(() => {
    const tools: Record<string, ClientToolHandler> = {};
    if (!nameKey) return tools;
    for (const name of nameKey.split(" ")) {
      tools[name] = makeGenerativeHandler(name, resolversRef.current, setItems);
    }
    return tools;
  }, [nameKey]);

  return { clientTools, items, respond, clear };
}
