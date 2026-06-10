Read the full contents of a workspace artifact by its key.

Use this tool to resolve a `workspace://<key>` reference that a previous tool
returned when its result was too large to inline — for example a large search
dump, a long catalog, or a big JSON array stored under `tool-results/`. The
reference looks like:

    Stored at workspace://tool-results/search_site-1717430400000-7.json (38 KB).
    Call workspace_read with key="tool-results/search_site-1717430400000-7.json" to read the full result when you need it.

Pass the `key` exactly as it appears in the reference (everything after
`workspace://`). Only call this tool when you actually need the offloaded
content to answer the user — otherwise leave it offloaded to keep the context
small.

Arguments:
- `key` (string, required): the workspace key to read, e.g.
  `tool-results/search_site-1717430400000-7.json`.

Returns the artifact's text content, or a short message if the key does not
exist or no conversation workspace is available.
