import { useCreateTerm, useDeleteTerm } from "@/api/queries/thesaurus.queries";
import { Input } from "@/components/ui/input";
import { GradientButton } from "@/components/ui/gradient-button";
import type { TurThesaurusTerm } from "@/models/kb/thesaurus.model";
import {
  IconChevronRight,
  IconCirclePlus,
  IconPencil,
  IconPlus,
  IconTag,
  IconTrash,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { ThesaurusTermEditor } from "./thesaurus-term-editor";

interface TreeNode {
  term: TurThesaurusTerm;
  children: TreeNode[];
}

/** Builds a forest from the flat term list via the soft `parentTermId`. */
function buildForest(terms: TurThesaurusTerm[]): TreeNode[] {
  const byId = new Map<string, TreeNode>();
  terms.forEach((term) => byId.set(term.id, { term, children: [] }));
  const roots: TreeNode[] = [];
  byId.forEach((node) => {
    const parentId = node.term.parentTermId;
    const parent = parentId ? byId.get(parentId) : undefined;
    if (parent) {
      parent.children.push(node);
    } else {
      roots.push(node);
    }
  });
  const sortNodes = (nodes: TreeNode[]) => {
    nodes.sort(
      (a, b) =>
        (a.term.termOrder ?? 0) - (b.term.termOrder ?? 0) ||
        a.term.label.localeCompare(b.term.label),
    );
    nodes.forEach((n) => sortNodes(n.children));
  };
  sortNodes(roots);
  return roots;
}

const RELATION_TONE: Record<string, string> = {
  RELATED: "bg-sky-500/15 text-sky-600 dark:text-sky-300",
  USE: "bg-amber-500/15 text-amber-600 dark:text-amber-300",
  USED_FOR: "bg-emerald-500/15 text-emerald-600 dark:text-emerald-300",
  CUSTOM: "bg-slate-500/15 text-slate-600 dark:text-slate-300",
};

interface TermTreeProps {
  microId: string;
  terms: TurThesaurusTerm[];
}

/**
 * Block AQ (§XL) / T676 — a friendly, expandable hierarchical view of a
 * microthesaurus' terms with variation/relation chips and inline add/delete.
 * Full variation/relation editing is T679.
 */
export function ThesaurusTermTree({ microId, terms }: TermTreeProps) {
  const { t } = useTranslation();
  const forest = useMemo(() => buildForest(terms), [terms]);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [addingUnder, setAddingUnder] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [newLabel, setNewLabel] = useState("");

  const createTerm = useCreateTerm(microId);
  const deleteTerm = useDeleteTerm(microId);

  function toggle(id: string) {
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  async function addTerm(parentTermId: string | null) {
    if (!newLabel.trim()) {
      toast.error(t("thesaurus.termLabelRequired"));
      return;
    }
    try {
      await createTerm.mutateAsync({
        id: "",
        label: newLabel.trim(),
        enabled: true,
        parentTermId: parentTermId ?? undefined,
      } as TurThesaurusTerm);
      setNewLabel("");
      setAddingUnder(null);
      if (parentTermId) {
        setExpanded((prev) => new Set(prev).add(parentTermId));
      }
    } catch (err) {
      console.error(err);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  async function removeTerm(term: TurThesaurusTerm) {
    if (!window.confirm(t("thesaurus.deleteTermConfirm", { name: term.label }))) {
      return;
    }
    try {
      await deleteTerm.mutateAsync(term.id);
    } catch (err) {
      console.error(err);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  function renderNode(node: TreeNode, depth: number) {
    const { term, children } = node;
    const isOpen = expanded.has(term.id);
    const hasChildren = children.length > 0;
    const relationTypes = Array.from(new Set((term.relations ?? []).map((r) => r.type)));
    const variationCount = term.variations?.length ?? 0;

    return (
      <div key={term.id}>
        <div
          className="bento-tile bento-glass group flex items-center gap-2 rounded-xl border border-border/50 px-3 py-2"
          style={{ marginLeft: depth * 20 }}
        >
          <button
            type="button"
            aria-label={hasChildren ? (isOpen ? t("thesaurus.collapse") : t("thesaurus.expand")) : t("thesaurus.leaf")}
            className={`grid size-5 shrink-0 place-items-center rounded transition-transform ${
              hasChildren ? "text-muted-foreground hover:text-foreground" : "opacity-30"
            } ${isOpen ? "rotate-90" : ""}`}
            onClick={() => hasChildren && toggle(term.id)}
            disabled={!hasChildren}
          >
            <IconChevronRight size={16} />
          </button>

          <span className={`min-w-0 flex-1 truncate text-sm ${term.enabled ? "" : "text-muted-foreground line-through"}`}>
            {term.label}
          </span>

          {variationCount > 0 && (
            <span className="hidden items-center gap-1 rounded-full bg-violet-500/15 px-2 py-0.5 text-xs text-violet-600 dark:text-violet-300 sm:inline-flex">
              <IconTag size={12} /> {variationCount}
            </span>
          )}
          {relationTypes.map((type) => (
            <span
              key={type}
              className={`hidden rounded-full px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide sm:inline-block ${
                RELATION_TONE[type] ?? RELATION_TONE.CUSTOM
              }`}
            >
              {t(`thesaurus.relation.${type}`, { defaultValue: type })}
            </span>
          ))}

          <div className="flex shrink-0 items-center gap-1 opacity-0 transition-opacity group-hover:opacity-100">
            <button
              type="button"
              aria-label={t("thesaurus.editTerm")}
              className="rounded-lg p-1 text-muted-foreground hover:bg-violet-500/10 hover:text-violet-500"
              onClick={() => setEditingId((cur) => (cur === term.id ? null : term.id))}
            >
              <IconPencil size={16} />
            </button>
            <button
              type="button"
              aria-label={t("thesaurus.addChild")}
              className="rounded-lg p-1 text-muted-foreground hover:bg-indigo-500/10 hover:text-indigo-500"
              onClick={() => {
                setAddingUnder(term.id);
                setNewLabel("");
                setExpanded((prev) => new Set(prev).add(term.id));
              }}
            >
              <IconCirclePlus size={16} />
            </button>
            <button
              type="button"
              aria-label={t("common.delete")}
              className="rounded-lg p-1 text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
              onClick={() => removeTerm(term)}
            >
              <IconTrash size={16} />
            </button>
          </div>
        </div>

        {editingId === term.id && (
          <div style={{ marginLeft: (depth + 1) * 20 }}>
            <ThesaurusTermEditor
              microId={microId}
              term={term}
              allTerms={terms}
              onClose={() => setEditingId(null)}
            />
          </div>
        )}

        {addingUnder === term.id && (
          <div className="mt-1 flex items-center gap-2" style={{ marginLeft: (depth + 1) * 20 }}>
            <Input
              autoFocus
              value={newLabel}
              onChange={(e) => setNewLabel(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && addTerm(term.id)}
              placeholder={t("thesaurus.newTermPlaceholder")}
              className="h-8"
            />
            <GradientButton type="button" className="h-8" onClick={() => addTerm(term.id)}>
              {t("forms.common.add")}
            </GradientButton>
            <button
              type="button"
              className="text-xs text-muted-foreground hover:text-foreground"
              onClick={() => setAddingUnder(null)}
            >
              {t("common.cancel")}
            </button>
          </div>
        )}

        {isOpen && children.map((child) => renderNode(child, depth + 1))}
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-1.5">
      {forest.map((node) => renderNode(node, 0))}
      {forest.length === 0 && (
        <p className="text-sm text-muted-foreground">{t("thesaurus.noTerms")}</p>
      )}

      {/* Add a root term */}
      {addingUnder === "__root__" ? (
        <div className="mt-2 flex items-center gap-2">
          <Input
            autoFocus
            value={newLabel}
            onChange={(e) => setNewLabel(e.target.value)}
            onKeyDown={(e) => e.key === "Enter" && addTerm(null)}
            placeholder={t("thesaurus.newTermPlaceholder")}
            className="h-8"
          />
          <GradientButton type="button" className="h-8" onClick={() => addTerm(null)}>
            {t("forms.common.add")}
          </GradientButton>
          <button
            type="button"
            className="text-xs text-muted-foreground hover:text-foreground"
            onClick={() => setAddingUnder(null)}
          >
            {t("common.cancel")}
          </button>
        </div>
      ) : (
        <button
          type="button"
          className="mt-2 inline-flex w-fit items-center gap-1.5 rounded-full border border-dashed border-border px-3 py-1.5 text-sm text-muted-foreground transition-colors hover:border-indigo-400 hover:text-indigo-500"
          onClick={() => {
            setAddingUnder("__root__");
            setNewLabel("");
          }}
        >
          <IconPlus size={16} />
          {t("thesaurus.addRootTerm")}
        </button>
      )}
    </div>
  );
}
