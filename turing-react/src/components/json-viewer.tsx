import { IconChevronDown, IconChevronRight } from "@tabler/icons-react";
import { useState } from "react";

type JsonValue =
  | JsonPrimitive
  | JsonArray
  | JsonObject;

type JsonPrimitive =
  | string
  | number
  | boolean
  | null;

interface JsonObject {
  [key: string]: JsonValue;
}

interface JsonArray extends Array<JsonValue> { }

interface JsonNodeProps {
  value: JsonValue;
  depth?: number;
}

function JsonNode({ value, depth = 0 }: JsonNodeProps) {
  const [collapsed, setCollapsed] = useState(depth >= 2);

  if (value === null)
    return <span className="text-rose-400">null</span>;

  if (typeof value === "boolean")
    return <span className="text-amber-400">{value.toString()}</span>;

  if (typeof value === "number")
    return <span className="text-sky-400">{value}</span>;

  if (typeof value === "string")
    return <span className="text-emerald-400">"{value}"</span>;

  if (Array.isArray(value)) {
    if (value.length === 0)
      return <span className="text-muted-foreground">{"[]"}</span>;

    return (
      <span>
        <button
          onClick={() => setCollapsed((c) => !c)}
          className="inline-flex items-center gap-0.5 text-muted-foreground hover:text-foreground"
        >
          {collapsed
            ? <IconChevronRight className="w-3 h-3" />
            : <IconChevronDown className="w-3 h-3" />}
          <span>{"["}</span>
        </button>
        {collapsed ? (
          <span
            className="text-muted-foreground italic text-xs cursor-pointer hover:text-foreground"
            onClick={() => setCollapsed(false)}
          >
            {" "}{value.length} items {"]"}
          </span>
        ) : (
          <>
            <div className="ml-4 border-l border-border pl-2">
              {value.map((item, i) => (
                <div key={i} className="leading-6">
                  <JsonNode value={item as JsonValue} depth={depth + 1} />
                  {i < value.length - 1 && (
                    <span className="text-muted-foreground">,</span>
                  )}
                </div>
              ))}
            </div>
            <span className="text-muted-foreground">{"]"}</span>
          </>
        )}
      </span>
    );
  }

  if (typeof value === "object") {
    const entries = Object.entries(value);
    if (entries.length === 0)
      return <span className="text-muted-foreground">{"{}"}</span>;

    return (
      <span>
        <button
          onClick={() => setCollapsed((c) => !c)}
          className="inline-flex items-center gap-0.5 text-muted-foreground hover:text-foreground"
        >
          {collapsed
            ? <IconChevronRight className="w-3 h-3" />
            : <IconChevronDown className="w-3 h-3" />}
          <span>{"{"}</span>
        </button>
        {collapsed ? (
          <span
            className="text-muted-foreground italic text-xs cursor-pointer hover:text-foreground"
            onClick={() => setCollapsed(false)}
          >
            {" "}{entries.length} keys {"} "}
          </span>
        ) : (
          <>
            <div className="ml-4 border-l border-border pl-2">
              {entries.map(([key, val], i) => (
                <div key={key} className="leading-6">
                  <span className="text-violet-400">"{key}"</span>
                  <span className="text-muted-foreground">: </span>
                  <JsonNode value={val} depth={depth + 1} />
                  {i < entries.length - 1 && (
                    <span className="text-muted-foreground">,</span>
                  )}
                </div>
              ))}
            </div>
            <span className="text-muted-foreground">{"}"}</span>
          </>
        )}
      </span>
    );
  }

  return <span>{String(value)}</span>;
}

interface JsonViewerProps {
  data: unknown;
  className?: string;
}

export function JsonViewer({ data, className }: JsonViewerProps) {
  return (
    <div
      className={`font-mono text-xs leading-6 bg-muted rounded-md p-4 overflow-auto ${className ?? ""}`}
    >
      <JsonNode value={data as JsonValue} depth={0} />
    </div>
  );
}
