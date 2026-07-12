import { IconCheck, IconUserCircle } from "@tabler/icons-react";

/**
 * One selectable persona in a {@link PersonaSelectGrid}. Kept intentionally
 * minimal (id + name + optional subtitle) so the grid stays decoupled from any
 * particular persona model — each caller maps its own personas into this shape
 * and decides what the subtitle line says (kind label, role, nothing).
 */
export interface PersonaSelectOption {
  id: string;
  name: string;
  subtitle?: string;
}

interface PersonaSelectGridProps {
  /** The personas available to pick from. */
  personas: PersonaSelectOption[];
  /**
   * The currently selected ids, **in selection order**. Callers that care about
   * ordering (e.g. a dialogue's speaking order) read this array directly; the
   * grid appends on select and removes on deselect, preserving order.
   */
  selectedIds: string[];
  /** Called with the next ordered id array whenever a tile is toggled. */
  onChange: (ids: string[]) => void;
  /** When true, selected tiles show their 1-based position instead of a check. */
  ordered?: boolean;
  /** Shown when there are no personas to pick from. */
  emptyText?: string;
  /** Tailwind grid-column classes; defaults to a responsive 1/2/3 layout. */
  gridClassName?: string;
}

/**
 * A reusable frosted persona-selection grid (Block AT / §XLIII). Extracted from
 * the Persona Match workspace and shared with the persona dialogue (speaker
 * roster) and persona suggest (restrict-to-personas) surfaces so all three pick
 * personas the same way. Multi-select, order-preserving, keyboard-accessible.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function PersonaSelectGrid({
  personas,
  selectedIds,
  onChange,
  ordered = false,
  emptyText,
  gridClassName = "grid gap-2 sm:grid-cols-2 lg:grid-cols-3",
}: Readonly<PersonaSelectGridProps>) {
  const toggle = (id: string) => {
    onChange(
      selectedIds.includes(id)
        ? selectedIds.filter((x) => x !== id)
        : [...selectedIds, id],
    );
  };

  if (personas.length === 0) {
    return emptyText ? <p className="text-sm text-muted-foreground">{emptyText}</p> : null;
  }

  return (
    <div className={gridClassName}>
      {personas.map((persona) => {
        const index = selectedIds.indexOf(persona.id);
        const selected = index >= 0;
        return (
          <button
            key={persona.id}
            type="button"
            onClick={() => toggle(persona.id)}
            aria-pressed={selected}
            className={`bento-tile bento-tile-clickable flex items-center gap-3 rounded-2xl border px-3 py-2.5 text-left backdrop-blur transition-colors ${
              selected
                ? "border-rose-500/40 bg-rose-500/10"
                : "border-border/60 bg-card/40 hover:border-rose-500/30"
            }`}
          >
            <span
              className={`grid size-9 shrink-0 place-items-center rounded-xl text-sm font-semibold ${
                selected
                  ? "bg-linear-to-br from-rose-600 to-pink-600 text-white"
                  : "bg-muted text-muted-foreground"
              }`}
            >
              {selected && ordered ? index + 1 : selected ? <IconCheck size={18} /> : <IconUserCircle size={18} />}
            </span>
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-medium">{persona.name}</div>
              {persona.subtitle && (
                <div className="truncate text-xs text-muted-foreground">{persona.subtitle}</div>
              )}
            </div>
          </button>
        );
      })}
    </div>
  );
}
