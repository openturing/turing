import { useEffect, useRef, useState } from "react"

interface D2DiagramProps {
  code: string
}

let d2Promise: Promise<import("@terrastruct/d2").D2> | null = null

function getD2Instance() {
  d2Promise ??= import("@terrastruct/d2").then(({ D2 }) => new D2())
  return d2Promise
}

async function renderWithWasm(code: string): Promise<string | null> {
  const d2 = await getD2Instance()
  const compileOpts = { layout: "dagre", noXMLTag: true, pad: 20, themeID: 0, darkThemeID: 200 }
  const result = await (d2.compile as (input: string, options?: Record<string, unknown>) => Promise<import("@terrastruct/d2").CompileResponse>)(code, compileOpts)
  const rendered = await d2.render(result.diagram, result.renderOptions)
  return typeof rendered === "string" ? rendered : null
}

async function renderWithDevServer(code: string): Promise<string> {
  const res = await fetch("/api/d2/render", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ code }),
  })
  const data = await res.json() as { svg?: string; error?: string }
  if (!res.ok || data.error) throw new Error(data.error ?? "Dev server D2 render failed")
  return data.svg ?? ""
}

async function renderD2(code: string): Promise<string> {
  // Try browser WASM first (works in production build)
  const wasmResult = await renderWithWasm(code).catch(() => null)
  if (wasmResult) return wasmResult

  // Fallback to dev server endpoint (Node.js WASM, works in dev)
  return renderWithDevServer(code)
}

export function D2Diagram({ code }: Readonly<D2DiagramProps>) {
  const [svg, setSvg] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    let cancelled = false

    renderD2(code).then((svgStr) => {
      if (!cancelled) { setSvg(svgStr); setError(null) }
    }).catch((err) => {
      if (!cancelled) { setError(err instanceof Error ? err.message : String(err)); setSvg(null) }
    })

    return () => { cancelled = true }
  }, [code])

  if (error) {
    return (
      <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
        <p className="font-medium mb-1">D2 diagram error</p>
        <pre className="text-xs whitespace-pre-wrap">{error}</pre>
      </div>
    )
  }

  if (!svg) {
    return (
      <div className="flex items-center justify-center rounded-lg border bg-muted/50 p-6">
        <div className="size-5 animate-spin rounded-full border-2 border-muted-foreground/30 border-t-muted-foreground" />
      </div>
    )
  }

  return (
    <div
      ref={containerRef}
      className="not-prose rounded-lg border bg-background p-2 overflow-x-auto [&_svg]:max-w-full [&_svg]:h-auto"
      dangerouslySetInnerHTML={{ __html: svg }}
    />
  )
}
