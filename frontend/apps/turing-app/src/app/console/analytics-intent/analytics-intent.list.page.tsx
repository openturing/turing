import {
  useAnalyticsIntents,
  useCreateAnalyticsIntent,
  useDeleteAnalyticsIntent,
  useReindexAnalyticsIntents,
  useUpdateAnalyticsIntent,
} from "@/api/queries/analytics-intent.queries";
import { LoadProvider } from "@/components/loading-provider";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { GradientButton } from "@/components/ui/gradient-button";
import { GradientSwitch } from "@/components/ui/gradient-switch";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import type { TurAnalyticsIntent } from "@/models/analytics-intent/analytics-intent.model";
import { IconChartBar, IconPencil, IconPlus, IconRefresh, IconTrash } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";
import { DialogDelete } from "@/components/dialog.delete";

/**
 * T28 / §III.5 Phase C — admin UI for the per-agent analytics intent
 * catalog. The catalog is small per agent (typically <50 rows) and the
 * data shape is trivial (label + samples bag + enabled flag), so a
 * single page with an inline modal for create/edit beats the
 * list+settings split used by the heavier {@code intent/} module.
 *
 * <p>The "Reindex" header button calls
 * {@code POST /api/ai-agent/{agentId}/analytics-intent/reindex} — the
 * Phase B bootstrap path that flushes the catalog into the SE-side
 * index used by {@code TurSeMltIntentClassifier}. Useful right after
 * binding an SE on the agent settings form, or after a Solr/ES schema
 * change.
 */
export default function AnalyticsIntentListPage() {
  const { t } = useTranslation();
  const { id: agentId } = useParams() as { id: string };
  const { data: intents, isError } = useAnalyticsIntents(agentId);
  const error = isError
    ? t("common.connectionError", { resource: t("analyticsIntent.title").toLowerCase() })
    : null;

  const createMutation = useCreateAnalyticsIntent();
  const updateMutation = useUpdateAnalyticsIntent();
  const deleteMutation = useDeleteAnalyticsIntent();
  const reindexMutation = useReindexAnalyticsIntents();

  const [editing, setEditing] = useState<TurAnalyticsIntent | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<TurAnalyticsIntent | null>(null);
  const [deleteOpen, setDeleteOpen] = useState(false);

  function openNew() {
    setEditing({ label: "", samples: "", description: "", enabled: 1 });
    setEditOpen(true);
  }

  function openEdit(intent: TurAnalyticsIntent) {
    setEditing({ ...intent });
    setEditOpen(true);
  }

  async function onSave() {
    if (!editing) return;
    if (!editing.label?.trim()) {
      toast.error(t("analyticsIntent.labelRequired"));
      return;
    }
    try {
      if (editing.id) {
        await updateMutation.mutateAsync({ agentId, intent: editing });
        toast.success(t("analyticsIntent.saved", { name: editing.label }));
      } else {
        await createMutation.mutateAsync({ agentId, intent: editing });
        toast.success(t("analyticsIntent.created", { name: editing.label }));
      }
      setEditOpen(false);
      setEditing(null);
    } catch (e) {
      console.error(e);
      toast.error(t("analyticsIntent.notSaved", { name: editing.label }));
    }
  }

  async function onDelete() {
    if (!deleteTarget) return;
    try {
      await deleteMutation.mutateAsync({ agentId, intent: deleteTarget });
      toast.success(t("analyticsIntent.deleted", { name: deleteTarget.label }));
    } catch (e) {
      console.error(e);
      toast.error(t("analyticsIntent.notDeleted", { name: deleteTarget.label }));
    }
    setDeleteOpen(false);
    setDeleteTarget(null);
  }

  async function onReindex() {
    try {
      const result = await reindexMutation.mutateAsync(agentId);
      toast.success(t("analyticsIntent.reindexed", {
        count: result.indexed,
        indexName: result.indexName,
      }));
    } catch (e) {
      console.error(e);
      toast.error(t("analyticsIntent.notReindexed"));
    }
  }

  return (
    <LoadProvider checkIsNotUndefined={intents} error={error}>
      <div className="px-4 lg:px-6 py-2 pb-8">
      <div className="flex items-center justify-between mb-4">
        <div>
          <h2 className="text-2xl font-bold flex items-center gap-2">
            <IconChartBar className="text-blue-600" />
            {t("analyticsIntent.title")}
          </h2>
          <p className="text-sm text-muted-foreground mt-1">
            {t("analyticsIntent.description")}
          </p>
        </div>
        <div className="flex gap-2">
          <GradientButton
            variant="outline"
            size="sm"
            onClick={onReindex}
            disabled={reindexMutation.isPending}>
            <IconRefresh className="mr-2 h-4 w-4" />
            {t("analyticsIntent.reindex")}
          </GradientButton>
          <GradientButton size="sm" onClick={openNew}>
            <IconPlus className="mr-2 h-4 w-4" />
            {t("analyticsIntent.newInstance")}
          </GradientButton>
        </div>
      </div>

      {intents && intents.length === 0 ? (
        <div className="text-center py-12 border border-dashed rounded-lg">
          <IconChartBar className="mx-auto h-12 w-12 text-muted-foreground" />
          <h3 className="mt-4 text-lg font-semibold">{t("analyticsIntent.blankTitle")}</h3>
          <p className="mt-2 text-sm text-muted-foreground">
            {t("analyticsIntent.blankDescription")}
          </p>
        </div>
      ) : (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("analyticsIntent.fields.label")}</TableHead>
              <TableHead>{t("analyticsIntent.fields.samplesPreview")}</TableHead>
              <TableHead className="w-24">{t("analyticsIntent.fields.enabled")}</TableHead>
              <TableHead className="w-32 text-right">{t("common.actions")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {intents?.map((intent) => (
              <TableRow key={intent.id} className="cursor-pointer" onClick={() => openEdit(intent)}>
                <TableCell className="font-medium">{intent.label}</TableCell>
                <TableCell className="text-muted-foreground text-sm">
                  {samplesPreview(intent.samples)}
                </TableCell>
                <TableCell>
                  {intent.enabled === 1 ? t("common.yes") : t("common.no")}
                </TableCell>
                <TableCell className="text-right" onClick={(e) => e.stopPropagation()}>
                  <Button variant="ghost" size="sm" onClick={() => openEdit(intent)}>
                    <IconPencil className="h-4 w-4" />
                  </Button>
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={() => {
                      setDeleteTarget(intent);
                      setDeleteOpen(true);
                    }}>
                    <IconTrash className="h-4 w-4 text-red-600" />
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <AnalyticsIntentDialog
        open={editOpen}
        onOpenChange={setEditOpen}
        intent={editing}
        onChange={setEditing}
        onSave={onSave}
      />

      {deleteTarget && (
        <DialogDelete
          open={deleteOpen}
          setOpen={setDeleteOpen}
          feature={t("analyticsIntent.title")}
          name={deleteTarget.label}
          onDelete={onDelete}
        />
      )}
      </div>
    </LoadProvider>
  );
}

function samplesPreview(samples?: string | null): string {
  if (!samples) return "—";
  const lines = samples.split("\n").map((s) => s.trim()).filter(Boolean);
  if (lines.length === 0) return "—";
  if (lines.length === 1) return truncate(lines[0], 60);
  return `${truncate(lines[0], 50)} (+${lines.length - 1})`;
}

function truncate(text: string, max: number): string {
  return text.length <= max ? text : text.substring(0, max) + "…";
}

interface DialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  intent: TurAnalyticsIntent | null;
  onChange: (intent: TurAnalyticsIntent) => void;
  onSave: () => void;
}

function AnalyticsIntentDialog({ open, onOpenChange, intent, onChange, onSave }: DialogProps) {
  const { t } = useTranslation();
  // Local mirror so the user can edit without firing a re-render on the
  // parent on every keystroke. Synced when the parent's intent ref
  // changes (open / edit-different-row).
  const [local, setLocal] = useState<TurAnalyticsIntent | null>(intent);
  useEffect(() => setLocal(intent), [intent]);

  if (!local) return null;

  function update<K extends keyof TurAnalyticsIntent>(key: K, value: TurAnalyticsIntent[K]) {
    if (!local) return;
    const next = { ...local, [key]: value };
    setLocal(next);
    onChange(next);
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[600px]">
        <DialogHeader>
          <DialogTitle>
            {local.id ? t("analyticsIntent.editTitle") : t("analyticsIntent.newInstance")}
          </DialogTitle>
          <DialogDescription>
            {t("analyticsIntent.dialogDescription")}
          </DialogDescription>
        </DialogHeader>
        <div className="grid gap-4 py-2">
          <div className="grid gap-2">
            <Label htmlFor="label">{t("analyticsIntent.fields.label")}</Label>
            <Input
              id="label"
              value={local.label ?? ""}
              onChange={(e) => update("label", e.target.value)}
              placeholder={t("analyticsIntent.placeholders.label")}
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="samples">{t("analyticsIntent.fields.samples")}</Label>
            <Textarea
              id="samples"
              rows={8}
              value={local.samples ?? ""}
              onChange={(e) => update("samples", e.target.value)}
              placeholder={t("analyticsIntent.placeholders.samples")}
            />
            <p className="text-xs text-muted-foreground">
              {t("analyticsIntent.samplesHint")}
            </p>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="description">{t("analyticsIntent.fields.description")}</Label>
            <Textarea
              id="description"
              rows={3}
              value={local.description ?? ""}
              onChange={(e) => update("description", e.target.value)}
              placeholder={t("analyticsIntent.placeholders.description")}
            />
          </div>
          <div className="flex items-center gap-2">
            <GradientSwitch
              id="enabled"
              checked={local.enabled === 1}
              onCheckedChange={(checked: boolean) => update("enabled", checked ? 1 : 0)}
            />
            <Label htmlFor="enabled">{t("analyticsIntent.fields.enabled")}</Label>
          </div>
        </div>
        <DialogFooter>
          <GradientButton variant="outline" size="sm" onClick={() => onOpenChange(false)}>
            {t("common.cancel")}
          </GradientButton>
          <GradientButton size="sm" onClick={onSave}>{t("common.save")}</GradientButton>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
