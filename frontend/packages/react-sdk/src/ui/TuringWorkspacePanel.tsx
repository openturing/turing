import type { ReactNode } from "react";
import type { TurWorkspaceArtifact } from "../core/api";
import {
  useTuringWorkspace,
  type UseTuringWorkspaceOptions,
} from "../hooks/use-turing-workspace";

export interface TuringWorkspacePanelProps extends UseTuringWorkspaceOptions {
  /** Heading shown above the artifact list. Defaults to "Files". */
  readonly title?: string;
  /** Custom renderer for a single artifact row (overrides the default link). */
  readonly itemComponent?: (props: {
    artifact: TurWorkspaceArtifact;
    index: number;
  }) => ReactNode;
  /** Rendered when the workspace has no artifacts yet. */
  readonly emptyComponent?: () => ReactNode;
  readonly className?: string;
}

/**
 * Drop-in sidebar that renders the live list of artifacts an agent has built
 * in the current conversation's workspace (T113) — "files this agent built for
 * you". Subscribes to the workspace SSE stream via {@link useTuringWorkspace};
 * every {@code put}/{@code delete} the agent performs updates the list in
 * &lt;100&nbsp;ms with no polling.
 *
 * <p>Headless-ish, like the other SDK UI components: structural markup +
 * caller-supplied {@code className}, with a render-prop ({@code itemComponent})
 * for full control over each row. Wrap in a {@code <TuringProvider>} (for the
 * site name) and pass the {@code agentId} so the initial snapshot back-fills.
 *
 * @example
 * ```tsx
 * <TuringWorkspacePanel agentId={agentId} className="workspace-sidebar" />
 * ```
 *
 * @since 2026.3.1
 */
export function TuringWorkspacePanel({
  title = "Files",
  itemComponent,
  emptyComponent,
  className,
  ...workspaceOptions
}: TuringWorkspacePanelProps) {
  const { artifacts, status } = useTuringWorkspace(workspaceOptions);

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

/** Default row: a download link to the signed URL + a human-readable size. */
function DefaultArtifactRow({ artifact }: { readonly artifact: TurWorkspaceArtifact }) {
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
function formatBytes(bytes: number): string {
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
