import type { ReactNode } from "react";

/**
 * Minimal structural shape of one workspace artifact — redeclared locally so
 * this package stays zero-runtime-dependency (it cannot import the core
 * `@viglet/turing-sdk` `TurWorkspaceArtifact` without breaking viglet.com's
 * `file:` install; see the html/d2 splitter + `TuringResultList` precedent).
 * Structurally compatible with the SDK type, so `@viglet/turing-react-sdk` can
 * forward its artifacts here with no consumer churn.
 */
export interface TuringWorkspaceArtifact {
  readonly key: string;
  readonly contentType: string | null;
  readonly size: number;
  readonly signedUrl: string | null;
}

/** Lifecycle of the workspace subscription, mirroring the SDK hook's status. */
export type TuringWorkspaceStatus = "idle" | "loading" | "success" | "error";

export interface TuringWorkspacePanelViewProps {
  /** The artifacts to render (a full snapshot, not deltas). */
  readonly artifacts: readonly TuringWorkspaceArtifact[];
  /** Subscription status — drives the default empty/error message. */
  readonly status?: TuringWorkspaceStatus;
  /** Heading shown above the artifact list. Defaults to "Files". */
  readonly title?: string;
  /** Custom renderer for a single artifact row (overrides the default link). */
  readonly itemComponent?: (props: {
    artifact: TuringWorkspaceArtifact;
    index: number;
  }) => ReactNode;
  /** Rendered when the workspace has no artifacts yet. */
  readonly emptyComponent?: () => ReactNode;
  readonly className?: string;
}

/**
 * Headless view half of the workspace panel — renders the live list of
 * artifacts an agent has built in a conversation's workspace (T113). Pure
 * presentation: structural `aside/ul/li` markup tagged with `data-turing-*`
 * hooks, a `formatBytes` helper for the default row, and a caller-supplied
 * `className` / `itemComponent` render prop for full control. It carries **no**
 * SSE/API coupling — the subscription lives in the data-wiring wrapper
 * (`TuringWorkspacePanel` in `@viglet/turing-react-sdk`), which subscribes via
 * `useTuringWorkspace` and forwards `artifacts` + `status` here (T308 split).
 * That lets viglet.com render the same list with its own Next transport,
 * axios-free.
 *
 * @example
 * ```tsx
 * <TuringWorkspacePanelView artifacts={artifacts} status={status} className="sidebar" />
 * ```
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
export function TuringWorkspacePanelView({
  artifacts,
  status,
  title = "Files",
  itemComponent,
  emptyComponent,
  className,
}: Readonly<TuringWorkspacePanelViewProps>) {
  return (
    <aside className={className} aria-label={title}>
      <h3>{title}</h3>
      {artifacts.length === 0 ? (
        emptyComponent ? (
          emptyComponent()
        ) : (
          <p data-turing-workspace-empty>
            {status === "error" ? "Workspace unavailable." : "No files yet."}
          </p>
        )
      ) : (
        <ul role="list" data-turing-workspace-list>
          {artifacts.map((artifact, index) => (
            <li key={artifact.key} role="listitem">
              {itemComponent ? (
                itemComponent({ artifact, index })
              ) : (
                <DefaultArtifactRow artifact={artifact} />
              )}
            </li>
          ))}
        </ul>
      )}
    </aside>
  );
}

TuringWorkspacePanelView.displayName = "TuringWorkspacePanelView";

/** Default row: a download link to the signed URL + a human-readable size. */
function DefaultArtifactRow({
  artifact,
}: {
  readonly artifact: TuringWorkspaceArtifact;
}) {
  return (
    <a
      href={artifact.signedUrl ?? "#"}
      target="_blank"
      rel="noopener noreferrer"
      download
      data-turing-workspace-item
      title={artifact.contentType ?? undefined}
    >
      <span data-turing-workspace-key>{artifact.key}</span>
      <span data-turing-workspace-size>{formatBytes(artifact.size)}</span>
    </a>
  );
}

/** Compact, locale-free byte formatter (B / KB / MB / GB). */
export function formatBytes(bytes: number): string {
  if (!bytes || bytes < 0) return "0 B";
  const units = ["B", "KB", "MB", "GB", "TB"];
  const exponent = Math.min(
    Math.floor(Math.log(bytes) / Math.log(1024)),
    units.length - 1,
  );
  const value = bytes / Math.pow(1024, exponent);
  const rounded = exponent === 0 ? value : Math.round(value * 10) / 10;
  return `${rounded} ${units[exponent]}`;
}
