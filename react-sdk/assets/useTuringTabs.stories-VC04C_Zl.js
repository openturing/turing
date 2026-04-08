import{a as e,n as t}from"./chunk-BneVvdWh.js";import{t as n}from"./react-B_0iYUWB.js";import{t as r}from"./jsx-runtime-BRDTPpDF.js";import{i,o as a,r as o,t as s,u as c}from"./fixtures-CxDbZb1C.js";function l({tabs:e}){let[t,n]=(0,u.useState)(0),r=e[t];return(0,d.jsxs)(`div`,{style:{fontFamily:`system-ui, sans-serif`,maxWidth:`700px`},children:[(0,d.jsx)(`div`,{style:{display:`flex`,gap:`0`,borderBottom:`2px solid #e2e8f0`,marginBottom:`16px`},children:e.map((e,r)=>(0,d.jsxs)(`button`,{onClick:()=>n(r),style:{padding:`10px 20px`,fontSize:`14px`,fontWeight:r===t?600:400,color:r===t?`#4f46e5`:`#64748b`,background:`transparent`,border:`none`,borderBottom:r===t?`2px solid #4f46e5`:`2px solid transparent`,cursor:`pointer`,marginBottom:`-2px`,display:`flex`,alignItems:`center`,gap:`6px`},children:[(0,d.jsx)(`span`,{children:e.icon}),e.label,(0,d.jsx)(`span`,{style:{fontSize:`10px`,padding:`1px 6px`,borderRadius:`8px`,background:r===t?`#eef2ff`:`#f1f5f9`,color:r===t?`#4f46e5`:`#94a3b8`},children:e.documents.length})]},e.label))}),(0,d.jsxs)(`div`,{children:[(0,d.jsxs)(`p`,{style:{fontSize:`13px`,color:`#64748b`,marginBottom:`12px`},children:[`Showing `,(0,d.jsx)(`strong`,{children:r.documents.length}),` results for tab "`,r.label,`"`]}),(0,d.jsx)(`div`,{style:{display:`flex`,flexDirection:`column`,gap:`8px`},children:r.documents.map(e=>(0,d.jsxs)(`div`,{style:{display:`flex`,gap:`12px`,padding:`12px`,borderRadius:`10px`,border:`1px solid #e2e8f0`,alignItems:`center`},children:[e.image&&(0,d.jsx)(`img`,{src:e.image,alt:e.title,style:{width:`60px`,height:`60px`,borderRadius:`8px`,objectFit:`cover`}}),(0,d.jsxs)(`div`,{style:{flex:1},children:[(0,d.jsx)(`div`,{style:{fontWeight:600,fontSize:`14px`},children:e.title}),(0,d.jsxs)(`div`,{style:{fontSize:`12px`,color:`#64748b`,marginTop:`2px`},children:[e.description.slice(0,80),`...`]})]})]},e.url))})]}),(0,d.jsxs)(`div`,{style:{marginTop:`24px`,padding:`12px 16px`,background:`#f8fafc`,borderRadius:`8px`,fontSize:`12px`,fontFamily:`monospace`,color:`#64748b`},children:[(0,d.jsx)(`span`,{style:{color:`#6d28d9`},children:`activeTab`}),`.label = "`,r.label,`" \xA0·\xA0`,(0,d.jsx)(`span`,{style:{color:`#6d28d9`},children:`activeTab`}),`.isActive = true \xA0·\xA0`,(0,d.jsx)(`span`,{style:{color:`#6d28d9`},children:`documents`}),`.length = `,r.documents.length]})]})}var u,d,f,p,m,h;t((()=>{u=e(n()),o(),d=r(),f={title:`Hooks/useTuringTabs`,component:l,tags:[`autodocs`],parameters:{layout:`padded`,docs:{description:{component:"Interactive tabs demo simulating `useTuringTabs`. Click tabs to switch datasets — this mirrors the behavior of tab-based search navigation."}}}},p={name:`Three Themes`,args:{tabs:[{label:`Creatures`,icon:`🐉`,documents:a(s)},{label:`Missions`,icon:`🚀`,documents:a(i)},{label:`Vinyl`,icon:`🎵`,documents:a(c)}]}},m={name:`Creatures Only`,args:{tabs:[{label:`All Creatures`,icon:`✨`,documents:a(s)},{label:`Dangerous Only`,icon:`☠️`,documents:a(s).filter(e=>e.raw.fields.danger_level>=8)}]}},p.parameters={...p.parameters,docs:{...p.parameters?.docs,source:{originalSource:`{
  name: "Three Themes",
  args: {
    tabs: [{
      label: "Creatures",
      icon: "🐉",
      documents: resolveDocsMock(creatureDocuments)
    }, {
      label: "Missions",
      icon: "🚀",
      documents: resolveDocsMock(missionDocuments)
    }, {
      label: "Vinyl",
      icon: "🎵",
      documents: resolveDocsMock(vinylDocuments)
    }]
  }
}`,...p.parameters?.docs?.source}}},m.parameters={...m.parameters,docs:{...m.parameters?.docs,source:{originalSource:`{
  name: "Creatures Only",
  args: {
    tabs: [{
      label: "All Creatures",
      icon: "✨",
      documents: resolveDocsMock(creatureDocuments)
    }, {
      label: "Dangerous Only",
      icon: "☠️",
      documents: resolveDocsMock(creatureDocuments).filter(d => d.raw.fields.danger_level as number >= 8)
    }]
  }
}`,...m.parameters?.docs?.source}}},h=[`ThreeThemes`,`TwoTabs`]}))();export{p as ThreeThemes,m as TwoTabs,h as __namedExportsOrder,f as default};