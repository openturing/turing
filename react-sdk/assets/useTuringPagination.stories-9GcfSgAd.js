import{a as e,n as t}from"./chunk-BneVvdWh.js";import{t as n}from"./react-B_0iYUWB.js";import{t as r}from"./jsx-runtime-BRDTPpDF.js";import{l as i,r as a,s as o}from"./fixtures-CxDbZb1C.js";function s({items:e,style:t}){let[n,r]=(0,c.useState)(1),i={FIRST:`«`,PREVIOUS:`‹`,NEXT:`›`,LAST:`»`};return(0,l.jsxs)(`div`,{style:{fontFamily:`system-ui, sans-serif`},children:[(0,l.jsxs)(`p`,{style:{fontSize:`13px`,color:`#64748b`,marginBottom:`12px`},children:[`Current page: `,(0,l.jsx)(`strong`,{children:n}),` · Click to navigate`]}),(0,l.jsx)(`nav`,{style:{display:`flex`,gap:`4px`,alignItems:`center`},children:e.map((e,a)=>{let o=e.page===n&&e.type!==`FIRST`&&e.type!==`LAST`&&e.type!==`PREVIOUS`&&e.type!==`NEXT`,s=e.type===`ELLIPSIS`||e.text===`...`,c=[`FIRST`,`PREVIOUS`,`NEXT`,`LAST`].includes(e.type);return s?(0,l.jsx)(`span`,{style:{padding:`0 8px`,color:`#94a3b8`},children:`…`},`e-${a}`):(0,l.jsx)(`button`,{style:(()=>{let e={display:`inline-flex`,alignItems:`center`,justifyContent:`center`,cursor:o?`default`:`pointer`,border:`none`,fontFamily:`monospace`,transition:`all 0.15s ease`};return t===`pills`?{...e,minWidth:`36px`,height:`36px`,borderRadius:`18px`,fontSize:c?`16px`:`13px`,fontWeight:o?700:500,background:o?`linear-gradient(135deg, #2563eb, #4f46e5)`:`#f1f5f9`,color:o?`white`:`#475569`}:t===`minimal`?{...e,minWidth:`32px`,height:`32px`,borderRadius:`0`,fontSize:c?`14px`:`13px`,fontWeight:o?700:400,background:`transparent`,color:o?`#4f46e5`:`#475569`,borderBottom:o?`2px solid #4f46e5`:`2px solid transparent`}:{...e,minWidth:`36px`,height:`36px`,borderRadius:`8px`,fontSize:c?`16px`:`13px`,fontWeight:o?700:500,background:o?`#4f46e5`:`transparent`,color:o?`white`:`#475569`}})(),disabled:o,onClick:()=>{!o&&e.page>0&&r(e.page)},children:c?i[e.type]:e.text},`${e.type}-${e.page}-${a}`)})})]})}var c,l,u,d,f,p,m,h;t((()=>{c=e(n()),a(),l=r(),u={title:`Hooks/useTuringPagination`,component:s,tags:[`autodocs`],parameters:{layout:`centered`,docs:{description:{component:"Interactive pagination demo. Click page numbers to navigate. This simulates the `useTuringPagination` hook behavior."}}}},d={name:`Default Style`,args:{items:o,style:`default`}},f={name:`Pill Style`,args:{items:o,style:`pills`}},p={name:`Minimal / Underline`,args:{items:o,style:`minimal`}},m={name:`Simple (3 Pages)`,args:{items:i,style:`default`}},d.parameters={...d.parameters,docs:{...d.parameters?.docs,source:{originalSource:`{
  name: "Default Style",
  args: {
    items: samplePagination,
    style: "default"
  }
}`,...d.parameters?.docs?.source}}},f.parameters={...f.parameters,docs:{...f.parameters?.docs,source:{originalSource:`{
  name: "Pill Style",
  args: {
    items: samplePagination,
    style: "pills"
  }
}`,...f.parameters?.docs?.source}}},p.parameters={...p.parameters,docs:{...p.parameters?.docs,source:{originalSource:`{
  name: "Minimal / Underline",
  args: {
    items: samplePagination,
    style: "minimal"
  }
}`,...p.parameters?.docs?.source}}},m.parameters={...m.parameters,docs:{...m.parameters?.docs,source:{originalSource:`{
  name: "Simple (3 Pages)",
  args: {
    items: simplePagination,
    style: "default"
  }
}`,...m.parameters?.docs?.source}}},h=[`DefaultStyle`,`PillStyle`,`MinimalStyle`,`FewPages`]}))();export{d as DefaultStyle,m as FewPages,p as MinimalStyle,f as PillStyle,h as __namedExportsOrder,u as default};