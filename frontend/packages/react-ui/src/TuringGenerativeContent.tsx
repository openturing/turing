import type { ComponentType, ReactNode } from "react";

/**
 * One generative-UI item to render (T440): a host-registered component the agent
 * asked for, by name, with the props it supplied. Produced by the React SDK's
 * `useGenerativeUI` hook when the agent calls a generative client tool (T438/T439).
 */
export interface TuringGenerativeItem {
  /** Stable id (the client-tool callId) correlating the item with its tool call. */
  id: string;
  /** Registered component name the agent invoked. */
  component: string;
  /** Props the agent supplied for the component. */
  props?: Record<string, unknown>;
}

/**
 * Props every registered generative component receives. {@link respond} resolves
 * the underlying client-tool call (T439) with a value — call it immediately for a
 * display-only component, or on user interaction for a picker/configurator. The
 * value flows back to the agent as the tool result and the turn continues.
 */
export interface TuringGenerativeComponentProps {
  props: Record<string, unknown>;
  respond: (value: unknown) => void;
}

/** Name → component map the host registers; the dispatch table for generative UI. */
export type TuringGenerativeRegistry = Record<
  string,
  ComponentType<TuringGenerativeComponentProps>
>;

/** Per-slot class names — the component ships ZERO visual styling of its own. */
export interface TuringGenerativeContentClassNames {
  /** The wrapping container around all items. */
  container?: string;
  /** Each item wrapper (carries `data-component`). */
  item?: string;
}

export interface TuringGenerativeContentProps {
  /** The generative items to render, in order (from `useGenerativeUI().items`). */
  items: ReadonlyArray<TuringGenerativeItem>;
  /** Name → component dispatch table the host registers. */
  registry: TuringGenerativeRegistry;
  /**
   * Resolves a rendered component's client-tool call. Wire to
   * `useGenerativeUI().respond`; the component calls its own `respond`, which
   * routes here with the item id.
   */
  onRespond: (id: string, value: unknown) => void;
  /** Optional render for an unregistered component name (default: nothing). */
  renderUnknown?: (item: TuringGenerativeItem) => ReactNode;
  /** Convenience alias for {@link TuringGenerativeContentClassNames.container}. */
  className?: string;
  classNames?: TuringGenerativeContentClassNames;
}

/**
 * Headless renderer for tool-driven generative UI (T440). Given the pending
 * generative items and a name→component registry, it dispatches each item to its
 * component — the same by-type dispatch model as {@link TuringRichContent} (here
 * keyed by component name instead of segment type), so generative segments and
 * markdown/html/d2 segments share one render shape. Each component is handed its
 * agent-supplied {@code props} and a {@code respond} callback that resolves the
 * client-tool call (T439) — call it now for display-only output, or on user
 * interaction for a picker.
 *
 * <p>Like the other `@viglet/turing-react-ui` primitives it ships no styling —
 * skin via `className`/`classNames`. Zero runtime dependencies. The admin chat
 * and viglet.com register their own component sets against this one renderer.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function TuringGenerativeContent({
  items,
  registry,
  onRespond,
  renderUnknown,
  className,
  classNames,
}: Readonly<TuringGenerativeContentProps>) {
  if (items.length === 0) return null;
  return (
    <div className={className ?? classNames?.container} data-turing-generative-content="">
      {items.map((item) => {
        const Component = registry[item.component];
        if (!Component) {
          return renderUnknown ? (
            <div key={item.id} className={classNames?.item} data-component={item.component}>
              {renderUnknown(item)}
            </div>
          ) : null;
        }
        return (
          <div key={item.id} className={classNames?.item} data-component={item.component}>
            <Component
              props={item.props ?? {}}
              respond={(value) => onRespond(item.id, value)}
            />
          </div>
        );
      })}
    </div>
  );
}
