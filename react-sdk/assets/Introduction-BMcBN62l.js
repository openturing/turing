import{n as e}from"./chunk-BneVvdWh.js";import{r as t,t as n}from"./react-X4udR1xK.js";import{t as r}from"./jsx-runtime-BRDTPpDF.js";import{a as i,o as a}from"./blocks-CbpAepsN.js";var o=e((()=>{n()}));function s(e){let n={a:`a`,code:`code`,h1:`h1`,h2:`h2`,h3:`h3`,hr:`hr`,li:`li`,p:`p`,pre:`pre`,strong:`strong`,ul:`ul`,...t(),...e.components};return(0,l.jsxs)(l.Fragment,{children:[(0,l.jsx)(i,{title:`Introduction`}),`
`,(0,l.jsx)(n.h1,{id:`-turing-react-sdk--storybook`,children:`🔍 Turing React SDK — Storybook`}),`
`,(0,l.jsxs)(n.p,{children:[`Welcome to the `,(0,l.jsx)(n.strong,{children:`@viglet/turing-react-sdk`}),` component library! This Storybook provides
interactive documentation and live examples for every hook and UI component in the SDK.`]}),`
`,(0,l.jsx)(n.hr,{}),`
`,(0,l.jsx)(n.h2,{id:`what-is-turing-react-sdk`,children:`What is Turing React SDK?`}),`
`,(0,l.jsxs)(n.p,{children:[`The Turing React SDK provides `,(0,l.jsx)(n.strong,{children:`headless hooks`}),` and `,(0,l.jsx)(n.strong,{children:`unstyled UI components`}),` for building
custom search experiences on top of the `,(0,l.jsx)(n.strong,{children:`Viglet Turing ES`}),` Semantic Navigation API.`]}),`
`,(0,l.jsx)(n.h3,{id:`architecture`,children:`Architecture`}),`
`,(0,l.jsx)(n.pre,{children:(0,l.jsx)(n.code,{children:`┌─────────────────────────────────────────┐
│             Your React App              │
│  ┌───────────────────────────────────┐  │
│  │         TuringProvider            │  │
│  │  (config, URL sync, search state) │  │
│  │  ┌─────────┐   ┌──────────────┐  │  │
│  │  │  Hooks   │   │ UI Components│  │  │
│  │  │ useTuring│   │ SearchBar    │  │  │
│  │  │ Search   │   │ ResultList   │  │  │
│  │  │ Facets   │   │ Pagination   │  │  │
│  │  │ Paginate │   │ SearchField  │  │  │
│  │  └─────────┘   └──────────────┘  │  │
│  └───────────────────────────────────┘  │
│                    │                     │
│            Turing REST API               │
└─────────────────────────────────────────┘
`})}),`
`,(0,l.jsx)(n.hr,{}),`
`,(0,l.jsx)(n.h2,{id:`-storybook-sections`,children:`📚 Storybook Sections`}),`
`,(0,l.jsx)(n.h3,{id:`ui-components`,children:`UI Components`}),`
`,(0,l.jsxs)(n.ul,{children:[`
`,(0,l.jsxs)(n.li,{children:[(0,l.jsx)(n.strong,{children:`TuringSearchBar`}),` — Simple search bar with customizable input/button render props`]}),`
`,(0,l.jsxs)(n.li,{children:[(0,l.jsx)(n.strong,{children:`TuringSearchField`}),` — Advanced compound component with autocomplete, history, and dropdown`]}),`
`,(0,l.jsxs)(n.li,{children:[(0,l.jsx)(n.strong,{children:`TuringResultList`}),` — Renders search results via slot/render-prop pattern`]}),`
`,(0,l.jsxs)(n.li,{children:[(0,l.jsx)(n.strong,{children:`TuringPagination`}),` — Renders Turing pagination items with navigation actions`]}),`
`]}),`
`,(0,l.jsx)(n.h3,{id:`hooks-interactive-demos`,children:`Hooks (Interactive Demos)`}),`
`,(0,l.jsxs)(n.ul,{children:[`
`,(0,l.jsxs)(n.li,{children:[(0,l.jsx)(n.strong,{children:`useTuringSearch`}),` — Core search hook for programmatic (non-URL) mode`]}),`
`,(0,l.jsxs)(n.li,{children:[(0,l.jsx)(n.strong,{children:`useTuringFacets`}),` — Facet data with toggle/clear actions`]}),`
`,(0,l.jsxs)(n.li,{children:[(0,l.jsx)(n.strong,{children:`useTuringPagination`}),` — Pagination data with navigation helpers`]}),`
`,(0,l.jsxs)(n.li,{children:[(0,l.jsx)(n.strong,{children:`useTuringDocument`}),` — Typed field access for custom document fields`]}),`
`,(0,l.jsxs)(n.li,{children:[(0,l.jsx)(n.strong,{children:`useTuringAutoComplete`}),` — Autocomplete with debouncing`]}),`
`,(0,l.jsxs)(n.li,{children:[(0,l.jsx)(n.strong,{children:`useTuringTabs`}),` — Tab-based search navigation`]}),`
`,(0,l.jsxs)(n.li,{children:[(0,l.jsx)(n.strong,{children:`useTuringSortOptions`}),` — Available sort options for a site`]}),`
`,(0,l.jsxs)(n.li,{children:[(0,l.jsx)(n.strong,{children:`useTuringSearchHistory`}),` — Persisted search history (IndexedDB)`]}),`
`]}),`
`,(0,l.jsx)(n.h3,{id:`showcases-inspired-by-turing-examples`,children:`Showcases (Inspired by turing-examples)`}),`
`,(0,l.jsxs)(n.ul,{children:[`
`,(0,l.jsxs)(n.li,{children:[(0,l.jsx)(n.strong,{children:`🐉 Mythical Creatures`}),` — Fantasy bestiary with element badges and danger bars`]}),`
`,(0,l.jsxs)(n.li,{children:[(0,l.jsx)(n.strong,{children:`🚀 Space Missions`}),` — Space exploration with agency/outcome facets and HUD cards`]}),`
`,(0,l.jsxs)(n.li,{children:[(0,l.jsx)(n.strong,{children:`🎵 Vinyl Records`}),` — Record store with album art, star ratings, and condition grading`]}),`
`]}),`
`,(0,l.jsx)(n.hr,{}),`
`,(0,l.jsx)(n.h2,{id:`quick-start`,children:`Quick Start`}),`
`,(0,l.jsx)(n.pre,{children:(0,l.jsx)(n.code,{className:`language-tsx`,children:`import { TuringProvider, useTuringSearch } from "@viglet/turing-react-sdk";

function App() {
  return (
    <TuringProvider config={{ site: "my-site", locale: "en_US" }}>
      <SearchPage />
    </TuringProvider>
  );
}

function SearchPage() {
  const { documents, searchQuery, status } = useTuringSearch();

  return (
    <div>
      <input
        placeholder="Search..."
        onKeyDown={(e) => {
          if (e.key === "Enter") searchQuery(e.currentTarget.value);
        }}
      />
      {status === "loading" && <p>Loading...</p>}
      {documents.map((doc) => (
        <div key={doc.url}>
          <h3>{doc.title}</h3>
          <p>{doc.description}</p>
        </div>
      ))}
    </div>
  );
}
`})}),`
`,(0,l.jsx)(n.hr,{}),`
`,(0,l.jsx)(n.h2,{id:`links`,children:`Links`}),`
`,(0,l.jsxs)(n.ul,{children:[`
`,(0,l.jsx)(n.li,{children:(0,l.jsx)(n.a,{href:`https://github.com/openviglet/turing`,rel:`nofollow`,children:`GitHub Repository`})}),`
`,(0,l.jsx)(n.li,{children:(0,l.jsx)(n.a,{href:`https://github.com/openviglet/turing/packages`,rel:`nofollow`,children:`npm Package`})}),`
`,(0,l.jsx)(n.li,{children:(0,l.jsx)(n.a,{href:`https://docs.viglet.org/turing/`,rel:`nofollow`,children:`Viglet Turing Documentation`})}),`
`]})]})}function c(e={}){let{wrapper:n}={...t(),...e.components};return n?(0,l.jsx)(n,{...e,children:(0,l.jsx)(s,{...e})}):s(e)}var l;e((()=>{l=r(),o(),a()}))();export{c as default};