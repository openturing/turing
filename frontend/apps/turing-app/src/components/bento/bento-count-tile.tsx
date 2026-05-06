import type { ComponentType } from "react";
import { Link } from "react-router-dom";
import { BENTO_TONE_GRADIENTS, type BentoTone } from "./bento-tones";

export interface BentoCountTileProps {
  to?: string;
  icon: ComponentType<{ size?: number }>;
  tone: BentoTone;
  label: string;
  count: number;
  span: string;
}

/**
 * Compact Bento tile that highlights a single number — typical for
 * counters (e.g., "12 LLMs", "3 MCP servers").
 */
export function BentoCountTile({
  to,
  icon: Icon,
  tone,
  label,
  count,
  span,
}: Readonly<BentoCountTileProps>) {
  const content = (
    <>
      <span className={`grid h-9 w-9 place-items-center rounded-2xl bg-linear-to-br ${BENTO_TONE_GRADIENTS[tone]} text-white shadow-md`}>
        <Icon size={18} />
      </span>
      <div>
        <div className="text-3xl font-semibold tabular-nums tracking-tight">{count}</div>
        <div className="text-[11px] uppercase tracking-wider text-muted-foreground">{label}</div>
      </div>
    </>
  );

  const className = `bento-tile bento-glass group relative flex flex-col justify-between overflow-hidden p-4 ${span}${to ? " bento-tile-clickable" : ""}`;

  return to
    ? <Link to={to} className={className}>{content}</Link>
    : <div className={className}>{content}</div>;
}
