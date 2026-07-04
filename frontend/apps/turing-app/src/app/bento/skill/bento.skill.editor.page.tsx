import { ROUTES } from "@/app/routes.const";
import SkillEditorPage from "@/app/console/skill/skill-editor.page";

/**
 * Bento skill folder editor — T560. The mini-VS-Code file-tree + CodeMirror
 * editor (T318) is reused verbatim, passing `baseRoute={ROUTES.BENTO_SKILL}`
 * so its back button stays in the shell and a shell-aware height + frosted
 * frame so the full-height IDE fits inside the padded bento main column
 * instead of overflowing it. The `overflowHidden` ResizablePanel gotcha
 * (turing-resizable-panel skill) is already handled inside the editor.
 */
export default function BentoSkillEditorPage() {
  return (
    <SkillEditorPage
      baseRoute={ROUTES.BENTO_SKILL}
      containerClassName="flex flex-col h-[calc(100svh-11rem)] overflow-hidden rounded-2xl border border-border/60 bg-card/40 backdrop-blur-md"
    />
  );
}
