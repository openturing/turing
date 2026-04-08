import{n as e}from"./chunk-BneVvdWh.js";import{t}from"./jsx-runtime-BRDTPpDF.js";import{l as n,r,s as i}from"./fixtures-CxDbZb1C.js";function a(e){return e.split(`_`).map(e=>e.charAt(0).toUpperCase()+e.slice(1).toLowerCase()).join(` `)}function o({items:e,onNavigate:t,itemComponent:n,className:r}){return e.length?(0,s.jsx)(`nav`,{className:r,"aria-label":`Pagination`,children:e.map((e,r)=>{let i=e.type===`CURRENT`,o=e.type===`ELLIPSIS`||e.text===`...`,c=a(e.text),l=()=>{!i&&!o&&e.href&&t(e.href)};return n?(0,s.jsx)(`span`,{children:n({item:e,isCurrent:i,isEllipsis:o,onClick:l,label:c})},`${e.type}-${e.page}-${r}`):o?(0,s.jsx)(`span`,{"aria-hidden":`true`,children:`…`},`ellipsis-${r}`):(0,s.jsx)(`button`,{type:`button`,onClick:l,disabled:i,"aria-current":i?`page`:void 0,"aria-label":`Page ${e.text}`,children:c},`${e.type}-${e.page}-${r}`)})}):null}var s,c=e((()=>{s=t(),o.__docgenInfo={description:`Pagination component that renders Turing pagination items.

@since 2026.2.0`,methods:[],displayName:`TuringPagination`,props:{items:{required:!0,tsType:{name:`Array`,elements:[{name:`TurPaginationItem`}],raw:`TurPaginationItem[]`},description:``},onNavigate:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(href: string) => void`,signature:{arguments:[{type:{name:`string`},name:`href`}],return:{name:`void`}}},description:``},itemComponent:{required:!1,tsType:{name:`signature`,type:`function`,raw:`(props: {
  item: TurPaginationItem;
  isCurrent: boolean;
  isEllipsis: boolean;
  onClick: () => void;
  label: string;
}) => ReactNode`,signature:{arguments:[{type:{name:`signature`,type:`object`,raw:`{
  item: TurPaginationItem;
  isCurrent: boolean;
  isEllipsis: boolean;
  onClick: () => void;
  label: string;
}`,signature:{properties:[{key:`item`,value:{name:`TurPaginationItem`,required:!0}},{key:`isCurrent`,value:{name:`boolean`,required:!0}},{key:`isEllipsis`,value:{name:`boolean`,required:!0}},{key:`onClick`,value:{name:`signature`,type:`function`,raw:`() => void`,signature:{arguments:[],return:{name:`void`}},required:!0}},{key:`label`,value:{name:`string`,required:!0}}]}},name:`props`}],return:{name:`ReactNode`}}},description:`Custom render for each pagination entry`},className:{required:!1,tsType:{name:`string`},description:``}}}})),l,u,d,f,p,m,h,g,_;e((()=>{c(),r(),l=t(),{fn:u}=__STORYBOOK_MODULE_TEST__,d={title:`UI Components/TuringPagination`,component:o,tags:[`autodocs`],parameters:{layout:`centered`,docs:{description:{component:`Headless pagination component. Renders Turing API pagination items with support for custom item rendering.`}}},argTypes:{onNavigate:{description:`Callback when a pagination link is clicked. Receives the href string.`,action:`navigated`}}},f={args:{items:i,onNavigate:u()}},p={name:`Simple (3 Pages)`,args:{items:n,onNavigate:u()}},m={name:`Styled (Pill Buttons)`,args:{items:i,onNavigate:u(),itemComponent:({item:e,isCurrent:t,isEllipsis:n,onClick:r,label:i})=>{if(n)return(0,l.jsx)(`span`,{style:{display:`inline-flex`,alignItems:`center`,justifyContent:`center`,width:`36px`,height:`36px`,color:`#94a3b8`,fontWeight:500},children:`…`});let a=[`FIRST`,`PREVIOUS`,`NEXT`,`LAST`].includes(e.type);return(0,l.jsx)(`button`,{onClick:r,disabled:t,style:{display:`inline-flex`,alignItems:`center`,justifyContent:`center`,minWidth:`36px`,height:`36px`,padding:`0 10px`,borderRadius:`8px`,border:`none`,fontSize:a?`18px`:`13px`,fontWeight:t?700:500,fontFamily:`monospace`,cursor:t?`default`:`pointer`,background:t?`linear-gradient(135deg, #2563eb, #4f46e5)`:`transparent`,color:t?`white`:`#475569`,transition:`all 0.15s ease`},children:a?{FIRST:`«`,PREVIOUS:`‹`,NEXT:`›`,LAST:`»`}[e.type]:i})}}},h={name:`Dark Theme`,args:{items:i,onNavigate:u(),itemComponent:({item:e,isCurrent:t,isEllipsis:n,onClick:r,label:i})=>{if(n)return(0,l.jsx)(`span`,{style:{color:`#475569`,padding:`0 4px`},children:`…`});let a=[`FIRST`,`PREVIOUS`,`NEXT`,`LAST`].includes(e.type);return(0,l.jsx)(`button`,{onClick:r,disabled:t,style:{display:`inline-flex`,alignItems:`center`,justifyContent:`center`,minWidth:`32px`,height:`32px`,padding:`0 8px`,borderRadius:`6px`,border:t?`1px solid #3b82f6`:`1px solid #1e293b`,fontSize:`12px`,fontWeight:t?700:400,fontFamily:`monospace`,cursor:t?`default`:`pointer`,background:t?`#1e3a5f`:`#0f172a`,color:t?`#60a5fa`:`#94a3b8`},children:a?{FIRST:`⏮`,PREVIOUS:`◀`,NEXT:`▶`,LAST:`⏭`}[e.type]:i})}},decorators:[e=>(0,l.jsx)(`div`,{style:{display:`flex`,gap:`4px`,alignItems:`center`,background:`#020617`,padding:`16px 24px`,borderRadius:`12px`},children:(0,l.jsx)(e,{})})]},g={name:`No Pagination`,args:{items:[],onNavigate:u()}},f.parameters={...f.parameters,docs:{...f.parameters?.docs,source:{originalSource:`{
  args: {
    items: samplePagination,
    onNavigate: fn()
  }
}`,...f.parameters?.docs?.source},description:{story:'## Default (Unstyled)\n\nWithout `itemComponent`, renders basic `<button>` elements.\nCURRENT page is disabled, ELLIPSIS shows "…".',...f.parameters?.docs?.description}}},p.parameters={...p.parameters,docs:{...p.parameters?.docs,source:{originalSource:`{
  name: "Simple (3 Pages)",
  args: {
    items: simplePagination,
    onNavigate: fn()
  }
}`,...p.parameters?.docs?.source},description:{story:`## Simple (3 pages)

A compact pagination with just 3 pages and a Next button.`,...p.parameters?.docs?.description}}},m.parameters={...m.parameters,docs:{...m.parameters?.docs,source:{originalSource:`{
  name: "Styled (Pill Buttons)",
  args: {
    items: samplePagination,
    onNavigate: fn(),
    itemComponent: ({
      item,
      isCurrent,
      isEllipsis,
      onClick,
      label
    }) => {
      if (isEllipsis) {
        return <span style={{
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          width: "36px",
          height: "36px",
          color: "#94a3b8",
          fontWeight: 500
        }}>
            …
          </span>;
      }
      const isNav = ["FIRST", "PREVIOUS", "NEXT", "LAST"].includes(item.type);
      const navSymbols: Record<string, string> = {
        FIRST: "«",
        PREVIOUS: "‹",
        NEXT: "›",
        LAST: "»"
      };
      return <button onClick={onClick} disabled={isCurrent} style={{
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        minWidth: "36px",
        height: "36px",
        padding: "0 10px",
        borderRadius: "8px",
        border: "none",
        fontSize: isNav ? "18px" : "13px",
        fontWeight: isCurrent ? 700 : 500,
        fontFamily: "monospace",
        cursor: isCurrent ? "default" : "pointer",
        background: isCurrent ? "linear-gradient(135deg, #2563eb, #4f46e5)" : "transparent",
        color: isCurrent ? "white" : "#475569",
        transition: "all 0.15s ease"
      }}>
          {isNav ? navSymbols[item.type] : label}
        </button>;
    }
  }
}`,...m.parameters?.docs?.source},description:{story:`## Styled Pagination

Custom \`itemComponent\` with pill-style buttons and gradient active state.
This mirrors the style used in the turing-examples projects.`,...m.parameters?.docs?.description}}},h.parameters={...h.parameters,docs:{...h.parameters?.docs,source:{originalSource:`{
  name: "Dark Theme",
  args: {
    items: samplePagination,
    onNavigate: fn(),
    itemComponent: ({
      item,
      isCurrent,
      isEllipsis,
      onClick,
      label
    }) => {
      if (isEllipsis) {
        return <span style={{
          color: "#475569",
          padding: "0 4px"
        }}>…</span>;
      }
      const isNav = ["FIRST", "PREVIOUS", "NEXT", "LAST"].includes(item.type);
      const navSymbols: Record<string, string> = {
        FIRST: "⏮",
        PREVIOUS: "◀",
        NEXT: "▶",
        LAST: "⏭"
      };
      return <button onClick={onClick} disabled={isCurrent} style={{
        display: "inline-flex",
        alignItems: "center",
        justifyContent: "center",
        minWidth: "32px",
        height: "32px",
        padding: "0 8px",
        borderRadius: "6px",
        border: isCurrent ? "1px solid #3b82f6" : "1px solid #1e293b",
        fontSize: "12px",
        fontWeight: isCurrent ? 700 : 400,
        fontFamily: "monospace",
        cursor: isCurrent ? "default" : "pointer",
        background: isCurrent ? "#1e3a5f" : "#0f172a",
        color: isCurrent ? "#60a5fa" : "#94a3b8"
      }}>
          {isNav ? navSymbols[item.type] : label}
        </button>;
    }
  },
  decorators: [Story => <div style={{
    display: "flex",
    gap: "4px",
    alignItems: "center",
    background: "#020617",
    padding: "16px 24px",
    borderRadius: "12px"
  }}>
        <Story />
      </div>]
}`,...h.parameters?.docs?.source},description:{story:`## Dark Theme

Pagination styled for dark backgrounds, as used in the Space Missions example.`,...h.parameters?.docs?.description}}},g.parameters={...g.parameters,docs:{...g.parameters?.docs,source:{originalSource:`{
  name: "No Pagination",
  args: {
    items: [],
    onNavigate: fn()
  }
}`,...g.parameters?.docs?.source},description:{story:`## No Pagination

When no items are provided, nothing is rendered.`,...g.parameters?.docs?.description}}},_=[`Default`,`Simple`,`StyledPagination`,`DarkTheme`,`NoPagination`]}))();export{h as DarkTheme,f as Default,g as NoPagination,p as Simple,m as StyledPagination,_ as __namedExportsOrder,d as default};