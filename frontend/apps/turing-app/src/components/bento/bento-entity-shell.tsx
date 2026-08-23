// T990 -- shim. BentoEntityShell now lives in the design system; this module
// keeps its path and its names so the pages importing it need no edit.
export {
  BentoEntityShell,
  type BentoEntityShellProps,
  type BentoEntityShellRenderArgs,
  type BentoIdentity,
  type BentoShellFormState,
} from "@viglet/viglet-design-system/bento";
