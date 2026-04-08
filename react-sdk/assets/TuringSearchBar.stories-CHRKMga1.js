import{a as e,n as t}from"./chunk-BneVvdWh.js";import{t as n}from"./react-B_0iYUWB.js";import{t as r}from"./jsx-runtime-BRDTPpDF.js";function i({onSearch:e,defaultQuery:t=``,placeholder:n=`Search...`,renderInput:r,renderButton:i,className:s,children:c}){let[l,u]=(0,a.useState)(t),d=(0,a.useRef)(null),f=(0,a.useCallback)(t=>{t.preventDefault(),e(l||`*`)},[e,l]),p={ref:d,type:`search`,value:l,onChange:e=>u(e.target.value),placeholder:n,autoComplete:`off`,"aria-label":n};return(0,o.jsxs)(`form`,{onSubmit:f,className:s,role:`search`,children:[r?r(p):(0,o.jsx)(`input`,{...p}),i?i({onClick:()=>e(l||`*`)}):(0,o.jsx)(`button`,{type:`submit`,children:`Search`}),c]})}var a,o,s=t((()=>{a=e(n()),o=r(),i.displayName=`TuringSearchBar`,i.__docgenInfo={description:`Search bar with customizable input and button via render props.

@since 2026.2.0`,methods:[],displayName:`TuringSearchBar`,props:{onSearch:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(query: string) => void`,signature:{arguments:[{type:{name:`string`},name:`query`}],return:{name:`void`}}},description:``},defaultQuery:{required:!1,tsType:{name:`string`},description:``,defaultValue:{value:`""`,computed:!1}},placeholder:{required:!1,tsType:{name:`string`},description:``,defaultValue:{value:`"Search..."`,computed:!1}},renderInput:{required:!1,tsType:{name:`signature`,type:`function`,raw:`(props: InputHTMLAttributes<HTMLInputElement> & { ref: React.Ref<HTMLInputElement> }) => ReactNode`,signature:{arguments:[{type:{name:`intersection`,raw:`InputHTMLAttributes<HTMLInputElement> & { ref: React.Ref<HTMLInputElement> }`,elements:[{name:`InputHTMLAttributes`,elements:[{name:`HTMLInputElement`}],raw:`InputHTMLAttributes<HTMLInputElement>`},{name:`signature`,type:`object`,raw:`{ ref: React.Ref<HTMLInputElement> }`,signature:{properties:[{key:`ref`,value:{name:`ReactRef`,raw:`React.Ref<HTMLInputElement>`,elements:[{name:`HTMLInputElement`}],required:!0}}]}}]},name:`props`}],return:{name:`ReactNode`}}},description:`Render prop for the input element`},renderButton:{required:!1,tsType:{name:`signature`,type:`function`,raw:`(props: { onClick: () => void }) => ReactNode`,signature:{arguments:[{type:{name:`signature`,type:`object`,raw:`{ onClick: () => void }`,signature:{properties:[{key:`onClick`,value:{name:`signature`,type:`function`,raw:`() => void`,signature:{arguments:[],return:{name:`void`}},required:!0}}]}},name:`props`}],return:{name:`ReactNode`}}},description:`Render prop for the submit button`},className:{required:!1,tsType:{name:`string`},description:``},children:{required:!1,tsType:{name:`ReactNode`},description:``}}}})),c,l,u,d,f,p,m,h,g;t((()=>{s(),c=r(),{fn:l}=__STORYBOOK_MODULE_TEST__,u={title:`UI Components/TuringSearchBar`,component:i,tags:[`autodocs`],parameters:{layout:`centered`,docs:{description:{component:`Simple search bar with customizable input/button. Uses render props for full control over the appearance.`}}},argTypes:{onSearch:{description:`Callback fired when the user submits a search`,action:`searched`},placeholder:{description:`Placeholder text for the input`,control:`text`},defaultQuery:{description:`Pre-fill the input with a default query`,control:`text`}}},d={args:{onSearch:l(),placeholder:`Search...`}},f={name:`Styled Input`,args:{onSearch:l(),placeholder:`Search products...`,renderInput:e=>(0,c.jsx)(`input`,{...e,style:{padding:`10px 16px`,fontSize:`16px`,border:`2px solid #6366f1`,borderRadius:`8px`,outline:`none`,width:`300px`}})}},p={name:`Custom Button`,args:{onSearch:l(),placeholder:`Search creatures...`,renderInput:e=>(0,c.jsx)(`input`,{...e,style:{padding:`10px 16px`,fontSize:`14px`,border:`1px solid #e2e8f0`,borderRadius:`8px 0 0 8px`,outline:`none`,width:`260px`}}),renderButton:({onClick:e})=>(0,c.jsx)(`button`,{onClick:e,style:{padding:`10px 20px`,fontSize:`14px`,background:`linear-gradient(135deg, #2563eb, #4f46e5)`,color:`white`,border:`none`,borderRadius:`0 8px 8px 0`,cursor:`pointer`,fontWeight:600},children:`🔍 Search`})}},m={name:`Pre-filled Query`,args:{onSearch:l(),defaultQuery:`dragon`,placeholder:`Search...`}},h={name:`Card Style`,args:{onSearch:l(),placeholder:`What are you looking for?`},render:e=>(0,c.jsxs)(`div`,{style:{padding:`24px`,background:`white`,borderRadius:`16px`,boxShadow:`0 4px 24px rgba(0,0,0,0.08)`,maxWidth:`480px`},children:[(0,c.jsx)(`h3`,{style:{margin:`0 0 12px`,fontSize:`18px`,fontWeight:700},children:`🔍 Enterprise Search`}),(0,c.jsx)(i,{...e,className:`search-bar`,renderInput:e=>(0,c.jsx)(`input`,{...e,style:{width:`100%`,padding:`12px 16px`,fontSize:`15px`,border:`2px solid #e2e8f0`,borderRadius:`10px`,outline:`none`,marginBottom:`8px`,boxSizing:`border-box`}}),renderButton:({onClick:e})=>(0,c.jsx)(`button`,{onClick:e,style:{width:`100%`,padding:`10px`,fontSize:`14px`,fontWeight:600,background:`linear-gradient(135deg, #2563eb, #4f46e5)`,color:`white`,border:`none`,borderRadius:`10px`,cursor:`pointer`},children:`Search`})})]})},d.parameters={...d.parameters,docs:{...d.parameters?.docs,source:{originalSource:`{
  args: {
    onSearch: fn(),
    placeholder: "Search..."
  }
}`,...d.parameters?.docs?.source},description:{story:"## Default (Unstyled)\n\nWithout render props, the component renders a plain `<input>` and `<button>`.\nThis is the simplest usage — just provide `onSearch`.",...d.parameters?.docs?.description}}},f.parameters={...f.parameters,docs:{...f.parameters?.docs,source:{originalSource:`{
  name: "Styled Input",
  args: {
    onSearch: fn(),
    placeholder: "Search products...",
    renderInput: props => <input {...props} style={{
      padding: "10px 16px",
      fontSize: "16px",
      border: "2px solid #6366f1",
      borderRadius: "8px",
      outline: "none",
      width: "300px"
    }} />
  }
}`,...f.parameters?.docs?.source},description:{story:`## With Custom Styled Input

Use \`renderInput\` to fully control the input element's appearance.
The render prop receives all necessary props (value, onChange, ref, etc.)`,...f.parameters?.docs?.description}}},p.parameters={...p.parameters,docs:{...p.parameters?.docs,source:{originalSource:`{
  name: "Custom Button",
  args: {
    onSearch: fn(),
    placeholder: "Search creatures...",
    renderInput: props => <input {...props} style={{
      padding: "10px 16px",
      fontSize: "14px",
      border: "1px solid #e2e8f0",
      borderRadius: "8px 0 0 8px",
      outline: "none",
      width: "260px"
    }} />,
    renderButton: ({
      onClick
    }) => <button onClick={onClick} style={{
      padding: "10px 20px",
      fontSize: "14px",
      background: "linear-gradient(135deg, #2563eb, #4f46e5)",
      color: "white",
      border: "none",
      borderRadius: "0 8px 8px 0",
      cursor: "pointer",
      fontWeight: 600
    }}>
        🔍 Search
      </button>
  }
}`,...p.parameters?.docs?.source},description:{story:"## With Custom Button\n\nUse `renderButton` to replace the default submit button.\nThe render prop receives `{ onClick }` to trigger the search.",...p.parameters?.docs?.description}}},m.parameters={...m.parameters,docs:{...m.parameters?.docs,source:{originalSource:`{
  name: "Pre-filled Query",
  args: {
    onSearch: fn(),
    defaultQuery: "dragon",
    placeholder: "Search..."
  }
}`,...m.parameters?.docs?.source},description:{story:`## Pre-filled Query

Pass \`defaultQuery\` to pre-fill the search input.
Useful when navigating back to a search page with a saved query.`,...m.parameters?.docs?.description}}},h.parameters={...h.parameters,docs:{...h.parameters?.docs,source:{originalSource:`{
  name: "Card Style",
  args: {
    onSearch: fn(),
    placeholder: "What are you looking for?"
  },
  render: args => <div style={{
    padding: "24px",
    background: "white",
    borderRadius: "16px",
    boxShadow: "0 4px 24px rgba(0,0,0,0.08)",
    maxWidth: "480px"
  }}>
      <h3 style={{
      margin: "0 0 12px",
      fontSize: "18px",
      fontWeight: 700
    }}>
        🔍 Enterprise Search
      </h3>
      <TuringSearchBar {...args} className="search-bar" renderInput={props => <input {...props} style={{
      width: "100%",
      padding: "12px 16px",
      fontSize: "15px",
      border: "2px solid #e2e8f0",
      borderRadius: "10px",
      outline: "none",
      marginBottom: "8px",
      boxSizing: "border-box"
    }} />} renderButton={({
      onClick
    }) => <button onClick={onClick} style={{
      width: "100%",
      padding: "10px",
      fontSize: "14px",
      fontWeight: 600,
      background: "linear-gradient(135deg, #2563eb, #4f46e5)",
      color: "white",
      border: "none",
      borderRadius: "10px",
      cursor: "pointer"
    }}>
            Search
          </button>} />
    </div>
}`,...h.parameters?.docs?.source},description:{story:`## Full Example (Card Style)

A more complete example showing the search bar in a card layout,
similar to what you'd see in a real application header.`,...h.parameters?.docs?.description}}},g=[`Default`,`StyledInput`,`CustomButton`,`PrefilledQuery`,`CardStyle`]}))();export{h as CardStyle,p as CustomButton,d as Default,m as PrefilledQuery,f as StyledInput,g as __namedExportsOrder,u as default};