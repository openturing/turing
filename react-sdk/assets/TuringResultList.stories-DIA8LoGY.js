import{n as e}from"./chunk-BneVvdWh.js";import{t}from"./jsx-runtime-BRDTPpDF.js";import{i as n,o as r,r as i,t as a,u as o}from"./fixtures-CxDbZb1C.js";function s({documents:e,itemComponent:t,emptyComponent:n,loadingComponent:r,isLoading:i=!1,className:a}){return i&&r?(0,c.jsx)(`div`,{className:a,children:r()}):!e.length&&n?(0,c.jsx)(`div`,{className:a,children:n()}):(0,c.jsx)(`div`,{className:a,role:`list`,children:e.map((e,n)=>(0,c.jsx)(`div`,{role:`listitem`,children:t({document:e,raw:e.raw,index:n})},e.url||n))})}var c,l=e((()=>{c=t(),s.__docgenInfo={description:`Renders search results via a slot/render-prop pattern.

@since 2026.2.0`,methods:[],displayName:`TuringResultList`,props:{documents:{required:!0,tsType:{name:`Array`,elements:[{name:`ResolvedDocument`}],raw:`ResolvedDocument[]`},description:``},itemComponent:{required:!0,tsType:{name:`signature`,type:`function`,raw:`(props: { document: ResolvedDocument; raw: TurDocument; index: number }) => ReactNode`,signature:{arguments:[{type:{name:`signature`,type:`object`,raw:`{ document: ResolvedDocument; raw: TurDocument; index: number }`,signature:{properties:[{key:`document`,value:{name:`ResolvedDocument`,required:!0}},{key:`raw`,value:{name:`TurDocument`,required:!0}},{key:`index`,value:{name:`number`,required:!0}}]}},name:`props`}],return:{name:`ReactNode`}}},description:``},emptyComponent:{required:!1,tsType:{name:`signature`,type:`function`,raw:`() => ReactNode`,signature:{arguments:[],return:{name:`ReactNode`}}},description:``},loadingComponent:{required:!1,tsType:{name:`signature`,type:`function`,raw:`() => ReactNode`,signature:{arguments:[],return:{name:`ReactNode`}}},description:``},isLoading:{required:!1,tsType:{name:`boolean`},description:``,defaultValue:{value:`false`,computed:!1}},className:{required:!1,tsType:{name:`string`},description:``}}}})),u,d,f,p,m,h,g,_,v,y,b;e((()=>{l(),i(),u=t(),d={title:`UI Components/TuringResultList`,component:s,tags:[`autodocs`],parameters:{layout:`padded`,docs:{description:{component:`Headless result list component. Renders search results using a render-prop pattern for maximum customization.`}}}},f=r(a),p={name:`🐉 Creatures List`,args:{documents:f,itemComponent:({document:e,index:t})=>(0,u.jsxs)(`div`,{style:{display:`flex`,gap:`16px`,padding:`16px`,borderBottom:`1px solid #e2e8f0`,alignItems:`flex-start`},children:[e.image&&(0,u.jsx)(`img`,{src:e.image,alt:e.title,style:{width:`80px`,height:`60px`,objectFit:`cover`,borderRadius:`8px`}}),(0,u.jsxs)(`div`,{children:[(0,u.jsxs)(`div`,{style:{fontWeight:600,fontSize:`15px`,marginBottom:`4px`},children:[t+1,`. `,e.title]}),(0,u.jsx)(`div`,{style:{color:`#64748b`,fontSize:`13px`,lineHeight:1.5},children:e.description}),(0,u.jsx)(`div`,{style:{marginTop:`8px`,display:`flex`,gap:`6px`},children:(e.raw.fields.element||[]).map(e=>(0,u.jsx)(`span`,{style:{padding:`2px 8px`,fontSize:`11px`,borderRadius:`12px`,background:`#ede9fe`,color:`#6d28d9`,fontWeight:500},children:e},e))})]})]},e.url)}},m=r(n),h={name:`🚀 Missions Grid`,args:{documents:m,className:`missions-grid`,itemComponent:({document:e})=>{let t=e.raw.fields,n=t.outcome===`Success`?`#10b981`:t.outcome===`Failure`?`#ef4444`:`#f59e0b`;return(0,u.jsxs)(`div`,{style:{display:`inline-block`,width:`260px`,margin:`8px`,borderRadius:`12px`,border:`1px solid #1e293b`,background:`#0f172a`,color:`white`,overflow:`hidden`,verticalAlign:`top`},children:[(0,u.jsx)(`img`,{src:e.image,alt:e.title,style:{width:`100%`,height:`140px`,objectFit:`cover`}}),(0,u.jsxs)(`div`,{style:{padding:`12px`},children:[(0,u.jsx)(`div`,{style:{display:`inline-block`,padding:`2px 8px`,fontSize:`10px`,borderRadius:`12px`,background:n+`22`,color:n,fontWeight:700,border:`1px solid ${n}44`,marginBottom:`8px`},children:t.outcome}),(0,u.jsx)(`div`,{style:{fontWeight:700,fontSize:`14px`,fontFamily:`monospace`},children:e.title}),(0,u.jsxs)(`div`,{style:{color:`#94a3b8`,fontSize:`12px`,marginTop:`4px`},children:[e.description.slice(0,100),`...`]}),(0,u.jsxs)(`div`,{style:{display:`flex`,gap:`12px`,marginTop:`8px`,fontSize:`10px`,color:`#64748b`,fontFamily:`monospace`},children:[(0,u.jsxs)(`span`,{children:[`📅 `,t.launch_date]}),(0,u.jsxs)(`span`,{children:[`⏱ `,t.duration]})]})]})]})}}},g=r(o),_={name:`🎵 Vinyl Records`,args:{documents:g,itemComponent:({document:e})=>{let t=e.raw.fields,n=Math.round(t.rating||0);return(0,u.jsxs)(`div`,{style:{display:`inline-block`,width:`200px`,margin:`8px`,borderRadius:`12px`,border:`1px solid #e2e8f0`,background:`white`,overflow:`hidden`,verticalAlign:`top`},children:[(0,u.jsx)(`img`,{src:e.image,alt:e.title,style:{width:`100%`,aspectRatio:`1`,objectFit:`cover`}}),(0,u.jsxs)(`div`,{style:{padding:`12px`},children:[(0,u.jsx)(`div`,{style:{fontWeight:700,fontSize:`14px`,lineHeight:1.3},children:e.title}),(0,u.jsx)(`div`,{style:{color:`#64748b`,fontSize:`12px`,marginTop:`2px`},children:t.artist}),(0,u.jsxs)(`div`,{style:{marginTop:`4px`,fontSize:`13px`},children:[`★`.repeat(n),`☆`.repeat(5-n)]}),(0,u.jsxs)(`div`,{style:{display:`flex`,justifyContent:`space-between`,marginTop:`8px`},children:[(0,u.jsx)(`span`,{style:{fontSize:`10px`,padding:`2px 6px`,borderRadius:`8px`,background:`#f0fdf4`,color:`#16a34a`},children:t.condition}),(0,u.jsxs)(`span`,{style:{fontWeight:700,color:`#d97706`},children:[`$`,t.price]})]})]})]})}}},v={name:`Empty State`,args:{documents:[],itemComponent:()=>null,emptyComponent:()=>(0,u.jsxs)(`div`,{style:{padding:`48px`,textAlign:`center`,color:`#94a3b8`},children:[(0,u.jsx)(`div`,{style:{fontSize:`48px`,marginBottom:`12px`},children:`🔍`}),(0,u.jsx)(`div`,{style:{fontWeight:600,fontSize:`16px`,marginBottom:`4px`},children:`No results found`}),(0,u.jsx)(`div`,{style:{fontSize:`13px`},children:`Try adjusting your search or clearing filters.`})]})}},y={name:`Loading State`,args:{documents:[],isLoading:!0,itemComponent:()=>null,loadingComponent:()=>(0,u.jsxs)(`div`,{style:{padding:`48px`,textAlign:`center`,color:`#94a3b8`},children:[(0,u.jsx)(`div`,{style:{fontSize:`32px`,marginBottom:`12px`},children:`⏳`}),(0,u.jsx)(`div`,{style:{fontSize:`14px`},children:`Searching...`})]})}},p.parameters={...p.parameters,docs:{...p.parameters?.docs,source:{originalSource:`{
  name: "🐉 Creatures List",
  args: {
    documents: creatureDocs,
    itemComponent: ({
      document,
      index
    }) => <div key={document.url} style={{
      display: "flex",
      gap: "16px",
      padding: "16px",
      borderBottom: "1px solid #e2e8f0",
      alignItems: "flex-start"
    }}>
        {document.image && <img src={document.image} alt={document.title} style={{
        width: "80px",
        height: "60px",
        objectFit: "cover",
        borderRadius: "8px"
      }} />}
        <div>
          <div style={{
          fontWeight: 600,
          fontSize: "15px",
          marginBottom: "4px"
        }}>
            {index + 1}. {document.title}
          </div>
          <div style={{
          color: "#64748b",
          fontSize: "13px",
          lineHeight: 1.5
        }}>
            {document.description}
          </div>
          <div style={{
          marginTop: "8px",
          display: "flex",
          gap: "6px"
        }}>
            {(document.raw.fields.element as string[] || []).map((el: string) => <span key={el} style={{
            padding: "2px 8px",
            fontSize: "11px",
            borderRadius: "12px",
            background: "#ede9fe",
            color: "#6d28d9",
            fontWeight: 500
          }}>
                {el}
              </span>)}
          </div>
        </div>
      </div>
  }
}`,...p.parameters?.docs?.source},description:{story:`## Default (Creatures)

Simple list rendering with the mythical creatures dataset.
Each item shows title, description, and custom fields from \`doc.raw.fields\`.`,...p.parameters?.docs?.description}}},h.parameters={...h.parameters,docs:{...h.parameters?.docs,source:{originalSource:`{
  name: "🚀 Missions Grid",
  args: {
    documents: missionDocs,
    className: "missions-grid",
    itemComponent: ({
      document
    }) => {
      const fields = document.raw.fields;
      const outcomeColor = fields.outcome === "Success" ? "#10b981" : fields.outcome === "Failure" ? "#ef4444" : "#f59e0b";
      return <div style={{
        display: "inline-block",
        width: "260px",
        margin: "8px",
        borderRadius: "12px",
        border: "1px solid #1e293b",
        background: "#0f172a",
        color: "white",
        overflow: "hidden",
        verticalAlign: "top"
      }}>
          <img src={document.image} alt={document.title} style={{
          width: "100%",
          height: "140px",
          objectFit: "cover"
        }} />
          <div style={{
          padding: "12px"
        }}>
            <div style={{
            display: "inline-block",
            padding: "2px 8px",
            fontSize: "10px",
            borderRadius: "12px",
            background: outcomeColor + "22",
            color: outcomeColor,
            fontWeight: 700,
            border: \`1px solid \${outcomeColor}44\`,
            marginBottom: "8px"
          }}>
              {fields.outcome as string}
            </div>
            <div style={{
            fontWeight: 700,
            fontSize: "14px",
            fontFamily: "monospace"
          }}>
              {document.title}
            </div>
            <div style={{
            color: "#94a3b8",
            fontSize: "12px",
            marginTop: "4px"
          }}>
              {document.description.slice(0, 100)}...
            </div>
            <div style={{
            display: "flex",
            gap: "12px",
            marginTop: "8px",
            fontSize: "10px",
            color: "#64748b",
            fontFamily: "monospace"
          }}>
              <span>📅 {fields.launch_date as string}</span>
              <span>⏱ {fields.duration as string}</span>
            </div>
          </div>
        </div>;
    }
  }
}`,...h.parameters?.docs?.source},description:{story:`## Space Missions Grid

Card-grid layout showing space missions with outcome badges and metadata.`,...h.parameters?.docs?.description}}},_.parameters={..._.parameters,docs:{..._.parameters?.docs,source:{originalSource:`{
  name: "🎵 Vinyl Records",
  args: {
    documents: vinylDocs,
    itemComponent: ({
      document
    }) => {
      const fields = document.raw.fields;
      const stars = Math.round(fields.rating as number || 0);
      return <div style={{
        display: "inline-block",
        width: "200px",
        margin: "8px",
        borderRadius: "12px",
        border: "1px solid #e2e8f0",
        background: "white",
        overflow: "hidden",
        verticalAlign: "top"
      }}>
          <img src={document.image} alt={document.title} style={{
          width: "100%",
          aspectRatio: "1",
          objectFit: "cover"
        }} />
          <div style={{
          padding: "12px"
        }}>
            <div style={{
            fontWeight: 700,
            fontSize: "14px",
            lineHeight: 1.3
          }}>
              {document.title}
            </div>
            <div style={{
            color: "#64748b",
            fontSize: "12px",
            marginTop: "2px"
          }}>
              {fields.artist as string}
            </div>
            <div style={{
            marginTop: "4px",
            fontSize: "13px"
          }}>
              {"★".repeat(stars)}{"☆".repeat(5 - stars)}
            </div>
            <div style={{
            display: "flex",
            justifyContent: "space-between",
            marginTop: "8px"
          }}>
              <span style={{
              fontSize: "10px",
              padding: "2px 6px",
              borderRadius: "8px",
              background: "#f0fdf4",
              color: "#16a34a"
            }}>
                {fields.condition as string}
              </span>
              <span style={{
              fontWeight: 700,
              color: "#d97706"
            }}>
                \${fields.price as number}
              </span>
            </div>
          </div>
        </div>;
    }
  }
}`,..._.parameters?.docs?.source},description:{story:`## Vinyl Records

Album card layout with cover art, artist info, rating, and price.`,..._.parameters?.docs?.description}}},v.parameters={...v.parameters,docs:{...v.parameters?.docs,source:{originalSource:`{
  name: "Empty State",
  args: {
    documents: [],
    itemComponent: () => null,
    emptyComponent: () => <div style={{
      padding: "48px",
      textAlign: "center",
      color: "#94a3b8"
    }}>
        <div style={{
        fontSize: "48px",
        marginBottom: "12px"
      }}>🔍</div>
        <div style={{
        fontWeight: 600,
        fontSize: "16px",
        marginBottom: "4px"
      }}>
          No results found
        </div>
        <div style={{
        fontSize: "13px"
      }}>
          Try adjusting your search or clearing filters.
        </div>
      </div>
  }
}`,...v.parameters?.docs?.source},description:{story:`## Empty State

When no documents are returned, the \`emptyComponent\` is rendered.`,...v.parameters?.docs?.description}}},y.parameters={...y.parameters,docs:{...y.parameters?.docs,source:{originalSource:`{
  name: "Loading State",
  args: {
    documents: [],
    isLoading: true,
    itemComponent: () => null,
    loadingComponent: () => <div style={{
      padding: "48px",
      textAlign: "center",
      color: "#94a3b8"
    }}>
        <div style={{
        fontSize: "32px",
        marginBottom: "12px"
      }}>⏳</div>
        <div style={{
        fontSize: "14px"
      }}>Searching...</div>
      </div>
  }
}`,...y.parameters?.docs?.source},description:{story:"## Loading State\n\nWhen `isLoading` is true, the `loadingComponent` is rendered instead of results.",...y.parameters?.docs?.description}}},b=[`CreaturesList`,`MissionsGrid`,`VinylRecords`,`EmptyState`,`LoadingState`]}))();export{p as CreaturesList,v as EmptyState,y as LoadingState,h as MissionsGrid,_ as VinylRecords,b as __namedExportsOrder,d as default};