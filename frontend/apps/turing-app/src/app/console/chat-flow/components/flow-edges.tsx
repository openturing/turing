import {
  BaseEdge,
  MarkerType,
  getSmoothStepPath,
  type Edge,
  type EdgeProps,
  type EdgeTypes,
} from "@xyflow/react";

/**
 * Visual try/catch edges leaving the `failure` source handle on
 * {@code functionCall} and {@code scheduleAgent} nodes (T50). T49 wired the
 * routing — when a node has {@code continueOnFailure: true} and a tool /
 * routine fails, the engine takes the outgoing edge whose
 * {@code sourceHandle} equals {@link FAILURE_HANDLE}. T50 styles that edge
 * red + dashed + animated so authors can spot the recovery path at a glance.
 *
 * @since 2026.3.1
 */

/**
 * JSON wire contract mirror of {@code ChatFlowOps.FAILURE_HANDLE} (backend).
 * Pinned by {@code AdvanceOnFailureTest} — changing this would orphan every
 * persisted failure edge.
 */
export const FAILURE_HANDLE = "failure";

/** React Flow {@code edgeTypes} key for failure edges. */
export const FAILURE_EDGE_TYPE = "failure";

/** Default edge type for non-failure connections (matches the legacy default). */
export const DEFAULT_EDGE_TYPE = "smoothstep";

export function isFailureSourceHandle(handle: string | null | undefined): boolean {
  return typeof handle === "string" && handle.toLowerCase() === FAILURE_HANDLE;
}

const FAILURE_COLOR = "#e11d48";

/**
 * Returns the visual overlay (type + styling + marker + label) for an edge,
 * derived from its {@code sourceHandle}. Centralises the "which handle ⇒
 * which look" decision so {@code onConnect}, {@code onReconnect}, and
 * {@code deserializeGraph} stay in sync.
 */
export function failureEdgeOverlay(sourceHandle: string | null | undefined): Partial<Edge> {
  if (isFailureSourceHandle(sourceHandle)) {
    return {
      type: FAILURE_EDGE_TYPE,
      animated: true,
      markerEnd: {
        type: MarkerType.ArrowClosed,
        color: FAILURE_COLOR,
        width: 18,
        height: 18,
      },
    };
  }
  // Explicitly clear failure-specific visuals on the non-failure branch so
  // reconnecting an edge from a `failure` handle to a regular handle drops
  // the red dashed look (otherwise the old edge object would keep `animated`
  // / `markerEnd` from its previous incarnation).
  return { type: DEFAULT_EDGE_TYPE, animated: false, markerEnd: undefined };
}

/**
 * Custom React Flow edge for the failure handle. Renders a dashed red
 * smooth-step path so it reads visually as the "catch" branch of a try /
 * catch — same colour family as the red-rose source Handle disc on the
 * functionCall and scheduleAgent nodes.
 */
function FailureEdge({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
  style,
  markerEnd,
  label,
  labelStyle,
  labelShowBg,
  labelBgStyle,
  labelBgPadding,
  labelBgBorderRadius,
}: EdgeProps) {
  const [path, labelX, labelY] = getSmoothStepPath({
    sourceX,
    sourceY,
    targetX,
    targetY,
    sourcePosition,
    targetPosition,
  });
  return (
    <BaseEdge
      id={id}
      path={path}
      labelX={labelX}
      labelY={labelY}
      label={label}
      labelStyle={labelStyle}
      labelShowBg={labelShowBg}
      labelBgStyle={labelBgStyle}
      labelBgPadding={labelBgPadding}
      labelBgBorderRadius={labelBgBorderRadius}
      markerEnd={markerEnd}
      style={{
        stroke: FAILURE_COLOR,
        strokeWidth: 2,
        strokeDasharray: "6 4",
        ...style,
      }}
    />
  );
}

export const edgeTypes: EdgeTypes = {
  [FAILURE_EDGE_TYPE]: FailureEdge,
};
