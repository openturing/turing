---
name: turing-resizable-panel
description: The Turing shadcn ResizablePanel customization (components/ui/resizable.tsx) — the custom overflowHidden prop that overrides react-resizable-panels' internal overflow:auto wrapper, when to use it, and the react-resizable-panels v4 size-unit gotcha (numeric = pixels; use string "60" for percentages). Also covers the AI-authoring global ResizablePanelGroup + useGlobalSidePanel portal pattern. Use whenever working with ResizablePanel, ResizablePanelGroup, side-panel layouts, or side-by-side AI authoring.
---

# ResizablePanel Customization (shadcn)

The `ResizablePanel` component (`components/ui/resizable.tsx`) has a custom `overflowHidden` prop:

- `react-resizable-panels` injects an internal wrapper `div` with `style="overflow: auto"` that cannot be controlled via props
- `overflowHidden` prop applies `overflow: hidden` on the Panel element and `[&>div]:overflow-hidden!` (with `!important`) to override the inline style on the internal wrapper
- Use `<ResizablePanel overflowHidden>` when the panel content manages its own scroll (e.g., a Card with `overflow-auto` on its body)
- In `react-resizable-panels` v4, numeric sizes are pixels. Use strings for
  percentages (`defaultSize="60"`), otherwise panels can render tiny/squeezed.

## Frontend Authoring & Design System Notes

- AI authoring side-by-side mode uses one global `ResizablePanelGroup` in
  `ConsoleRootPage`; pages portal authoring content into the slot through
  `useGlobalSidePanel`.
- Viglet Design System source lives at
  `d:/git/viglet/viglet-design-system`; inspect `src/components/ui/` there
  when debugging design-system component behavior.
