import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { IconKeyboard } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export interface BentoShortcutsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** True on macOS — swaps the ⌘ glyph for Ctrl on every other platform. */
  isMac: boolean;
}

/**
 * The keyboard-shortcut guide (T572) — a reference sheet for the handful of
 * global + palette shortcuts the bento shell actually binds. Opened with `?`
 * from anywhere, or from the user menu. Kept honest: it lists only shortcuts
 * that really work (⌘K/Ctrl+K + `?` are global; the arrow/enter/esc set drives
 * the command palette), so pressing what's shown always does what's shown.
 */
export function BentoShortcutsDialog({ open, onOpenChange, isMac }: Readonly<BentoShortcutsDialogProps>) {
  const { t } = useTranslation();
  const mod = isMac ? "⌘" : "Ctrl";

  const groups: { heading: string; rows: { keys: string[]; label: string }[] }[] = [
    {
      heading: t("bento.shortcuts.global", { defaultValue: "Global" }),
      rows: [
        { keys: [mod, "K"], label: t("bento.palette.open", { defaultValue: "Open command palette" }) },
        { keys: ["?"], label: t("bento.shortcuts.openGuide", { defaultValue: "Show keyboard shortcuts" }) },
      ],
    },
    {
      heading: t("bento.shortcuts.palette", { defaultValue: "Command palette" }),
      rows: [
        { keys: ["↑", "↓"], label: t("bento.shortcuts.navigate", { defaultValue: "Move between results" }) },
        { keys: ["↵"], label: t("bento.shortcuts.openSelected", { defaultValue: "Open the selected area" }) },
        { keys: ["Esc"], label: t("bento.shortcuts.dismiss", { defaultValue: "Close the palette or a dialog" }) },
      ],
    },
  ];

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <IconKeyboard size={20} className="text-indigo-500" />
            {t("bento.shortcuts.title", { defaultValue: "Keyboard shortcuts" })}
          </DialogTitle>
          <DialogDescription>
            {t("bento.shortcuts.description", { defaultValue: "Move around Turing without leaving the keyboard." })}
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-5">
          {groups.map((group) => (
            <section key={group.heading}>
              <h3 className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                {group.heading}
              </h3>
              <ul className="flex flex-col gap-1.5">
                {group.rows.map((row) => (
                  <li key={row.label} className="flex items-center justify-between gap-4">
                    <span className="text-sm text-foreground">{row.label}</span>
                    <span className="flex shrink-0 items-center gap-1">
                      {row.keys.map((key) => (
                        <kbd
                          key={key}
                          className="inline-flex min-w-6 items-center justify-center rounded-md border border-border/60 bg-muted px-1.5 py-0.5 text-[11px] font-medium tracking-wide"
                        >
                          {key}
                        </kbd>
                      ))}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>
      </DialogContent>
    </Dialog>
  );
}
