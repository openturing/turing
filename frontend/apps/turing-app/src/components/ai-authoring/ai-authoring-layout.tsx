"use client"
import type { ReactNode } from "react"

import {
  ResizableHandle,
  ResizablePanel,
  ResizablePanelGroup,
} from "@/components/ui/resizable"

/**
 * Side-by-side layout for the AI Authoring pattern: form on the left,
 * chat companion on the right, separated by a draggable handle.
 *
 * <p>Hosted at PAGE level (not app shell) so the surrounding chrome —
 * page header / breadcrumb / SubPage sidebar — stays full-width above
 * and is NOT resized along with the form. The layout occupies a viewport
 * box (calc-100svh-headers) so the chat's textarea at the bottom is
 * always visible regardless of how long the form is.
 *
 * <p>Two load-bearing CSS bits:
 * <ol>
 *   <li>{@code h-[calc(100svh-9rem)]} — FIXED height (NOT min-h). Without
 *       this the panels grow with their content and the chat textarea
 *       slides off-screen on long forms.</li>
 *   <li>{@code *:min-w-0} on the group — Resizable panels render as
 *       flex items internally and default to {@code min-width:auto}
 *       (intrinsic content width), which would let a wide form (sticky
 *       buttons, code editor, section cards) jam the handle.</li>
 * </ol>
 *
 * <p>Size props are STRINGS by design — react-resizable-panels v4 reads
 * numeric values as pixels and string values without units as
 * percentages.
 *
 * @since 2026.2.5
 */

interface Props {
  /** Form content on the left panel — usually a regular page form. */
  readonly children: ReactNode
  /** Chat / companion content on the right panel. */
  readonly chat: ReactNode
}

export function AiAuthoringLayout({ children, chat }: Props) {
  return (
    <div className="h-[calc(100svh-9rem)] w-full">
      <ResizablePanelGroup orientation="horizontal" className="h-full w-full *:min-w-0">
        <ResizablePanel defaultSize="65" minSize="30">
          <div className="h-full overflow-auto">{children}</div>
        </ResizablePanel>
        <ResizableHandle withHandle />
        <ResizablePanel defaultSize="35" minSize="20" maxSize="70">
          <div className="h-full">{chat}</div>
        </ResizablePanel>
      </ResizablePanelGroup>
    </div>
  )
}
