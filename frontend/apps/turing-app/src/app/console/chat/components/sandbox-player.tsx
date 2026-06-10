import { IconCode, IconEye, IconMaximize, IconX } from "@tabler/icons-react"
import { TuringHtmlSandbox } from "@viglet/turing-react-sdk"

interface SandboxPlayerProps {
  /** The ```html fragment to preview. Matches the headless `{ code }` segment
   * shape so it can be passed straight to `TuringRichContent`'s `html` slot. */
  code: string
}

/**
 * Admin-console skin over the shared, headless {@link TuringHtmlSandbox}
 * (`@viglet/turing-react-ui`). The iframe sandbox / fullscreen / code-toggle
 * logic lives in the shared component; this adapter only supplies the console's
 * shadcn/Tailwind look (classNames), @tabler icons, and labels — the same
 * design as before, now with zero duplicated logic (viglet.com applies its own
 * skin to the same component).
 */
export function SandboxPlayer({ code }: Readonly<SandboxPlayerProps>) {
  return (
    <TuringHtmlSandbox
      code={code}
      height={300}
      showCodeToggle
      labels={{ title: "HTML Preview", fullscreen: "Open fullscreen", close: "Close" }}
      icons={{
        fullscreen: <IconMaximize className="size-3.5" />,
        close: <IconX className="size-5" />,
        code: <span className="flex items-center gap-1"><IconCode className="size-3.5" /> Code</span>,
        preview: <span className="flex items-center gap-1"><IconEye className="size-3.5" /> Preview</span>,
      }}
      classNames={{
        root: "not-prose rounded-lg border bg-background overflow-hidden",
        header: "flex items-center justify-between border-b bg-muted/50 px-3 py-1.5",
        label: "text-xs font-medium text-muted-foreground",
        actions: "flex items-center gap-1",
        button:
          "flex items-center gap-1 rounded px-2 py-0.5 text-xs text-muted-foreground hover:bg-muted hover:text-foreground transition-colors",
        iframe: "w-full bg-white",
        code: "overflow-auto p-3 text-xs bg-muted/30 max-h-[300px]",
        fullscreenRoot: "fixed inset-0 z-50 flex flex-col bg-background/95 backdrop-blur-sm",
        fullscreenHeader: "flex items-center justify-between border-b px-4 py-2 bg-muted/50",
        fullscreenLabel: "text-sm font-medium",
        fullscreenButton:
          "rounded-md p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground transition-colors",
        fullscreenIframe: "flex-1 w-full bg-white",
      }}
    />
  )
}
