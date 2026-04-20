import { IconCode, IconEye, IconMaximize, IconX } from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { createPortal } from "react-dom"

interface SandboxPlayerProps {
  htmlCode: string
}

function buildSrcDoc(htmlCode: string) {
  return `<!DOCTYPE html>
<html><head><meta charset="utf-8"><style>html,body{margin:0;padding:8px;font-family:system-ui,sans-serif;box-sizing:border-box;}</style></head>
<body>${htmlCode}</body></html>`
}

function FullscreenModal({ htmlCode, onClose }: { htmlCode: string; onClose: () => void }) {
  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => { if (e.key === "Escape") onClose() }
    document.addEventListener("keydown", handleKey)
    return () => document.removeEventListener("keydown", handleKey)
  }, [onClose])

  return createPortal(
    <div className="fixed inset-0 z-50 flex flex-col bg-background/95 backdrop-blur-sm">
      <div className="flex items-center justify-between border-b px-4 py-2 bg-muted/50">
        <span className="text-sm font-medium">HTML Preview</span>
        <button
          type="button"
          onClick={onClose}
          title="Close"
          className="rounded-md p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
        >
          <IconX className="size-5" />
        </button>
      </div>
      <iframe
        srcDoc={buildSrcDoc(htmlCode)}
        sandbox="allow-scripts"
        className="flex-1 w-full border-0 bg-white"
        title="HTML sandbox fullscreen"
      />
    </div>,
    document.body,
  )
}

export function SandboxPlayer({ htmlCode }: Readonly<SandboxPlayerProps>) {
  const [showCode, setShowCode] = useState(false)
  const [fullscreen, setFullscreen] = useState(false)

  return (
    <>
      <div className="not-prose rounded-lg border bg-background overflow-hidden">
        <div className="flex items-center justify-between border-b bg-muted/50 px-3 py-1.5">
          <span className="text-xs font-medium text-muted-foreground">HTML Preview</span>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => setFullscreen(true)}
              title="Open fullscreen"
              className="flex items-center gap-1 rounded px-2 py-0.5 text-xs text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
            >
              <IconMaximize className="size-3.5" />
            </button>
            <button
              type="button"
              onClick={() => setShowCode(!showCode)}
              className="flex items-center gap-1 rounded px-2 py-0.5 text-xs text-muted-foreground hover:bg-muted hover:text-foreground transition-colors"
            >
              {showCode ? <><IconEye className="size-3.5" /> Preview</> : <><IconCode className="size-3.5" /> Code</>}
            </button>
          </div>
        </div>
        {showCode ? (
          <pre className="overflow-auto p-3 text-xs bg-muted/30 max-h-[300px]"><code>{htmlCode}</code></pre>
        ) : (
          <iframe
            srcDoc={buildSrcDoc(htmlCode)}
            sandbox="allow-scripts"
            className="w-full border-0 bg-white"
            style={{ height: 300 }}
            title="HTML sandbox"
          />
        )}
      </div>
      {fullscreen && <FullscreenModal htmlCode={htmlCode} onClose={() => setFullscreen(false)} />}
    </>
  )
}
