import type { ReactNode } from "react";
import { TuringWorkspacePanelView } from "@viglet/turing-react-ui";
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
 * <p>This is the data-wiring half of the component: it owns the SSE
 * subscription and forwards the resulting {@code artifacts} + {@code status} to
 * the headless {@code TuringWorkspacePanelView} in
 * {@code @viglet/turing-react-sdk}'s react-ui peer (T308 split). The structural
 * markup, {@code formatBytes}, and the default row live there; this wrapper
 * keeps the same props, so consumers are unchanged. Wrap in a
 * {@code <TuringProvider>} (for the site name) and pass the {@code agentId} so
 * the initial snapshot back-fills.
 *
 * @example
 * ```tsx
 * <TuringWorkspacePanel agentId={agentId} className="workspace-sidebar" />
 * ```
 *
 * @since 2026.3.1
 */
export function TuringWorkspacePanel({
  title,
  itemComponent,
  emptyComponent,
  className,
  ...workspaceOptions
}: TuringWorkspacePanelProps) {
  const { artifacts, status } = useTuringWorkspace(workspaceOptions);

  return (
    <TuringWorkspacePanelView
      artifacts={artifacts}
      status={status}
      title={title}
      itemComponent={itemComponent}
      emptyComponent={emptyComponent}
      className={className}
    />
  );
}
