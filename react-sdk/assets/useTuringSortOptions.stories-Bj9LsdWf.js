import{a as e,n as t}from"./chunk-BneVvdWh.js";import{t as n}from"./react-B_0iYUWB.js";import{t as r}from"./jsx-runtime-BRDTPpDF.js";import{c as i,r as a}from"./fixtures-CxDbZb1C.js";function o({options:e,style:t}){let[n,r]=(0,s.useState)(e[0]?.value??``);return(0,c.jsxs)(`div`,{style:{fontFamily:`system-ui, sans-serif`},children:[(0,c.jsxs)(`p`,{style:{fontSize:`12px`,color:`#94a3b8`,marginBottom:`12px`},children:[`Active sort: `,(0,c.jsx)(`code`,{style:{color:`#6d28d9`},children:n})]}),t===`select`&&(0,c.jsx)(`select`,{value:n,onChange:e=>r(e.target.value),style:{padding:`8px 32px 8px 12px`,fontSize:`14px`,borderRadius:`8px`,border:`2px solid #e2e8f0`,outline:`none`,cursor:`pointer`,appearance:`auto`},children:e.map(e=>(0,c.jsx)(`option`,{value:e.value,children:e.label},e.value))}),t===`radio`&&(0,c.jsx)(`div`,{style:{display:`flex`,flexDirection:`column`,gap:`8px`},children:e.map(e=>(0,c.jsxs)(`label`,{style:{display:`flex`,alignItems:`center`,gap:`8px`,padding:`8px 12px`,borderRadius:`8px`,border:e.value===n?`2px solid #4f46e5`:`1px solid #e2e8f0`,background:e.value===n?`#eef2ff`:`white`,cursor:`pointer`,fontSize:`14px`},children:[(0,c.jsx)(`input`,{type:`radio`,name:`sort`,value:e.value,checked:e.value===n,onChange:()=>r(e.value),style:{accentColor:`#4f46e5`}}),(0,c.jsx)(`span`,{style:{fontWeight:e.value===n?600:400},children:e.label}),(0,c.jsx)(`span`,{style:{marginLeft:`auto`,fontSize:`11px`,color:`#94a3b8`,fontFamily:`monospace`},children:e.value})]},e.value))}),t===`pills`&&(0,c.jsx)(`div`,{style:{display:`flex`,gap:`6px`,flexWrap:`wrap`},children:e.map(e=>(0,c.jsx)(`button`,{onClick:()=>r(e.value),style:{padding:`6px 14px`,borderRadius:`20px`,border:`none`,fontSize:`13px`,fontWeight:e.value===n?600:400,cursor:`pointer`,background:e.value===n?`linear-gradient(135deg, #2563eb, #4f46e5)`:`#f1f5f9`,color:e.value===n?`white`:`#475569`},children:e.label},e.value))})]})}var s,c,l,u,d,f,p;t((()=>{s=e(n()),a(),c=r(),l={title:`Hooks/useTuringSortOptions`,component:o,tags:[`autodocs`],parameters:{layout:`centered`,docs:{description:{component:`Interactive sort options demo. Switch between select, radio, and pill styles to see how sort options can be presented.`}}}},u={name:`Select Dropdown`,args:{options:i,style:`select`}},d={name:`Radio Group`,args:{options:i,style:`radio`}},f={name:`Pill Selector`,args:{options:i,style:`pills`}},u.parameters={...u.parameters,docs:{...u.parameters?.docs,source:{originalSource:`{
  name: "Select Dropdown",
  args: {
    options: sampleSortOptions,
    style: "select"
  }
}`,...u.parameters?.docs?.source}}},d.parameters={...d.parameters,docs:{...d.parameters?.docs,source:{originalSource:`{
  name: "Radio Group",
  args: {
    options: sampleSortOptions,
    style: "radio"
  }
}`,...d.parameters?.docs?.source}}},f.parameters={...f.parameters,docs:{...f.parameters?.docs,source:{originalSource:`{
  name: "Pill Selector",
  args: {
    options: sampleSortOptions,
    style: "pills"
  }
}`,...f.parameters?.docs?.source}}},p=[`SelectDropdown`,`RadioGroup`,`PillSelector`]}))();export{f as PillSelector,d as RadioGroup,u as SelectDropdown,p as __namedExportsOrder,l as default};