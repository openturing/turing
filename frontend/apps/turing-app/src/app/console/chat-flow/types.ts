/**
 * Types for the Chat Flow builder. The shape mirrors what the backend Spring AI Advisor will
 * consume: a list of typed nodes (with AI instructions / routing metadata) and a list of edges.
 *
 * @since 2026.2.4
 */

export type FlowNodeType = "start" | "end" | "aiQuestion" | "condition" | "functionCall";

export interface FlowNodeData extends Record<string, unknown> {
  label: string;
  type: FlowNodeType;
  /** Prompt sent to the LLM when the node is an AI question or condition-on-AI step. */
  aiInstruction?: string;
  /** Variable name where the collected value is stored in the flow context (e.g. "email"). */
  outputVariable?: string;
  /** Validation rule for AI question outputs. */
  validationRule?: string;
  /** Target function name for Function Call nodes. */
  functionName?: string;
  /** Condition expression for Condition nodes. */
  conditionExpression?: string;
}

export const NODE_COLORS: Record<FlowNodeType, { bg: string; border: string; text: string }> = {
  start: {
    bg: "bg-emerald-500",
    border: "border-emerald-600",
    text: "text-white",
  },
  end: {
    bg: "bg-rose-500",
    border: "border-rose-600",
    text: "text-white",
  },
  aiQuestion: {
    bg: "bg-blue-50 dark:bg-blue-950/40",
    border: "border-blue-500",
    text: "text-blue-900 dark:text-blue-100",
  },
  condition: {
    bg: "bg-amber-50 dark:bg-amber-950/40",
    border: "border-amber-500",
    text: "text-amber-900 dark:text-amber-100",
  },
  functionCall: {
    bg: "bg-orange-50 dark:bg-orange-950/40",
    border: "border-orange-500",
    text: "text-orange-900 dark:text-orange-100",
  },
};
