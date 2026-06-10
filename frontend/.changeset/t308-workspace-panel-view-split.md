---
"@viglet/turing-react-ui": minor
"@viglet/turing-react-sdk": patch
---

Split `TuringWorkspacePanel` into a headless view + a thin SSE-wiring wrapper
(T308). The presentation half — the `aside/ul/li` structure, the default
download-link row, and the `formatBytes` helper — now ships from
`@viglet/turing-react-ui` as `TuringWorkspacePanelView`, taking `artifacts` +
`status` (plus `title`/`itemComponent`/`emptyComponent`) as props and carrying
no SSE/API coupling. `TuringWorkspacePanel` stays in `@viglet/turing-react-sdk`
as a one-call wrapper that subscribes via `useTuringWorkspace` and forwards the
snapshot to the view — same props, so consumers are unchanged. The artifact
shape is redeclared locally in react-ui (structurally compatible with the SDK's
`TurWorkspaceArtifact`) to keep the package zero-runtime-dep, letting viglet.com
render the workspace list with its own Next transport, axios-free.
