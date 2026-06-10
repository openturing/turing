---
"@viglet/turing-react-ui": minor
"@viglet/turing-react-sdk": patch
---

Split `TuringSearchField` into a headless view + a data-wiring wrapper (T307).
The presentation half — the compound `Input` / `Button` / `Dropdown` with the
suggestions/history dropdown — now ships from `@viglet/turing-react-ui` as
`TuringSearchFieldView`, driven entirely by props/context with **no baked-in
styling** (the old Tailwind defaults — `w-full px-4 py-2.5 hover:bg-gray-100 …`
— were stripped to honor the headless contract; visuals now come from
`className` + the dropdown render props). The orchestrating Root (autocomplete,
search-history, url-search hooks and the results→history save effect) stays in
`@viglet/turing-react-sdk` as `TuringSearchField`, which feeds the view its
state/callbacks and re-attaches `.Input` / `.Button` / `.Dropdown` — so consumer
markup (`<TuringSearchField.Input />`, the marketplace apps) is unchanged. Lets
viglet.com reuse the search field without pulling the axios SDK.
