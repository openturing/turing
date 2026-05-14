import { useCallback, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { toast } from "@viglet/viglet-design-system";
import { IconLayoutList, IconPencil, IconPlus, IconTrash } from "@tabler/icons-react";

import {
  useAIAgentSlots,
  useCreateAIAgentSlot,
  useDeleteAIAgentSlot,
  useUpdateAIAgentSlot,
} from "@/api/queries/ai-agent-slot.queries";
import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import { SubPageHeader } from "@/components/sub.page.header";
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
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { SmartDescription } from "@/components/ui/smart-description";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  TUR_AI_AGENT_SLOT_TYPES,
  type TurAIAgentSlot,
  type TurAIAgentSlotType,
} from "@/models/agent/ai-agent-slot.model";

interface FormState {
  name: string;
  type: TurAIAgentSlotType;
  description: string;
}

const EMPTY_FORM: FormState = { name: "", type: "STRING", description: "" };

const NAME_PATTERN = /^[A-Za-z_][A-Za-z0-9_]*$/;

/**
 * Inline CRUD of the agent's typed-slot catalogue. Authors create slots
 * here (name + primitive type); the chat-flow editor will later list these
 * in the {@code outputVariable} dropdown so values land in a stable schema.
 *
 * @since 2026.2.7
 */
export default function AIAgentSlotPage() {
  const { t } = useTranslation();
  const { id: agentId } = useParams() as { id: string };
  const baseUrl = `${ROUTES.AI_AGENT_INSTANCE}/${agentId}/slot`;

  const { data: slots, isError } = useAIAgentSlots(agentId);
  const createMutation = useCreateAIAgentSlot();
  const updateMutation = useUpdateAIAgentSlot();
  const deleteMutation = useDeleteAIAgentSlot();

  const [editing, setEditing] = useState<TurAIAgentSlot | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState<FormState>(EMPTY_FORM);
  const [nameError, setNameError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState<TurAIAgentSlot | null>(null);

  const openCreate = useCallback(() => {
    setEditing(null);
    setForm(EMPTY_FORM);
    setNameError(null);
    setDialogOpen(true);
  }, []);

  const openEdit = useCallback((slot: TurAIAgentSlot) => {
    setEditing(slot);
    setForm({
      name: slot.name,
      type: slot.type,
      description: slot.description ?? "",
    });
    setNameError(null);
    setDialogOpen(true);
  }, []);

  const onSave = useCallback(async () => {
    const trimmed = form.name.trim();
    if (!trimmed) {
      setNameError(t("aiAgent.slot.errors.nameRequired"));
      return;
    }
    if (!NAME_PATTERN.test(trimmed)) {
      setNameError(t("aiAgent.slot.errors.nameInvalid"));
      return;
    }
    const payload: TurAIAgentSlot = {
      id: editing?.id ?? "",
      name: trimmed,
      type: form.type,
      description: form.description.trim() || null,
    };
    try {
      if (editing) {
        await updateMutation.mutateAsync({ agentId, slot: payload });
        toast.success(t("aiAgent.slot.updated", { name: payload.name }));
      } else {
        await createMutation.mutateAsync({ agentId, slot: payload });
        toast.success(t("aiAgent.slot.created", { name: payload.name }));
      }
      setDialogOpen(false);
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status;
      if (status === 409) {
        setNameError(t("aiAgent.slot.errors.nameTaken"));
      } else if (status === 400) {
        setNameError(t("aiAgent.slot.errors.nameInvalid"));
      } else {
        toast.error(t("aiAgent.slot.saveFailed"));
      }
    }
  }, [agentId, editing, form, createMutation, updateMutation, t]);

  const onConfirmDelete = useCallback(async () => {
    if (!pendingDelete) return;
    try {
      await deleteMutation.mutateAsync({ agentId, slot: pendingDelete });
      toast.success(t("aiAgent.slot.deleted", { name: pendingDelete.name }));
    } catch {
      toast.error(t("aiAgent.slot.deleteFailed"));
    } finally {
      setPendingDelete(null);
    }
  }, [agentId, pendingDelete, deleteMutation, t]);

  const error = isError ? t("common.connectionError", { resource: t("aiAgent.slot.title").toLowerCase() }) : null;
  const saving = createMutation.isPending || updateMutation.isPending;
  const hasSlots = (slots?.length ?? 0) > 0;

  return (
    <LoadProvider checkIsNotUndefined={slots} error={error} tryAgainUrl={baseUrl}>
      <SubPageHeader
        icon={IconLayoutList}
        feature={t("aiAgent.slot.title")}
        name={t("aiAgent.slot.title")}
        description={t("aiAgent.slot.description")}
      />

      <div className="px-4 lg:px-6">
        {hasSlots ? (
          <>
            <div className="mb-4 flex justify-end">
              <GradientButton onClick={openCreate} size="sm">
                <IconPlus className="size-4 mr-1.5" />
                {t("aiAgent.slot.add")}
              </GradientButton>
            </div>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("aiAgent.slot.fields.name")}</TableHead>
                  <TableHead className="w-32">{t("aiAgent.slot.fields.type")}</TableHead>
                  <TableHead>{t("aiAgent.slot.fields.description")}</TableHead>
                  <TableHead className="w-28 text-right">{t("aiAgent.slot.fields.actions")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {slots?.map((slot) => (
                  <TableRow key={slot.id}>
                    <TableCell>
                      <code className="text-xs font-mono">{slot.name}</code>
                    </TableCell>
                    <TableCell>
                      <span className="inline-flex items-center rounded-md bg-muted px-2 py-0.5 text-xs font-medium">
                        {t(`aiAgent.slot.types.${slot.type}`)}
                      </span>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground max-w-0">
                      <div className="truncate" title={slot.description ?? ""}>
                        {slot.description ?? ""}
                      </div>
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="inline-flex gap-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => openEdit(slot)}
                          title={t("aiAgent.slot.edit")}
                        >
                          <IconPencil className="size-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setPendingDelete(slot)}
                          title={t("aiAgent.slot.delete")}
                        >
                          <IconTrash className="size-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </>
        ) : (
          <div className="flex flex-col items-center justify-center gap-4 py-16 text-center">
            <div className="flex h-14 w-14 items-center justify-center rounded-full bg-blue-500/10">
              <IconLayoutList className="size-7 text-blue-600 dark:text-blue-400" />
            </div>
            <div className="space-y-1.5 max-w-md">
              <h2 className="text-lg font-semibold">{t("aiAgent.slot.blankTitle")}</h2>
              <p className="text-sm text-muted-foreground">
                {t("aiAgent.slot.blankDescription")}
              </p>
            </div>
            <GradientButton onClick={openCreate} size="sm">
              <IconPlus className="size-4 mr-1.5" />
              {t("aiAgent.slot.add")}
            </GradientButton>
          </div>
        )}
      </div>

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              {editing ? t("aiAgent.slot.editTitle") : t("aiAgent.slot.addTitle")}
            </DialogTitle>
            <DialogDescription>
              {t("aiAgent.slot.dialogDescription")}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-2">
            <div className="space-y-1.5">
              <Label htmlFor="slot-name">{t("aiAgent.slot.fields.name")}</Label>
              <Input
                id="slot-name"
                value={form.name}
                onChange={(e) => {
                  setForm({ ...form, name: e.target.value });
                  if (nameError) setNameError(null);
                }}
                placeholder={t("aiAgent.slot.namePlaceholder")}
                autoFocus
              />
              {nameError && (
                <p className="text-xs text-red-600 dark:text-red-400">{nameError}</p>
              )}
              <p className="text-xs text-muted-foreground">
                {t("aiAgent.slot.nameHint")}
              </p>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="slot-type">{t("aiAgent.slot.fields.type")}</Label>
              <Select
                value={form.type}
                onValueChange={(v) => setForm({ ...form, type: v as TurAIAgentSlotType })}
              >
                <SelectTrigger id="slot-type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {TUR_AI_AGENT_SLOT_TYPES.map((type) => (
                    <SelectItem key={type} value={type}>
                      {t(`aiAgent.slot.types.${type}`)}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <SmartDescription
                value={form.description}
                onChange={(value) => setForm((prev) => ({ ...prev, description: value }))}
                placeholder={t("aiAgent.slot.descriptionPlaceholder")}
                rows={3}
                title={form.name}
                entityType={t("aiAgent.slot.title")}
                enableMetaPrompt
              >
                <SmartDescription.Label>
                  {t("aiAgent.slot.fields.description")}
                </SmartDescription.Label>
              </SmartDescription>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDialogOpen(false)}>
              {t("common.cancel")}
            </Button>
            <GradientButton onClick={onSave} disabled={saving}>
              {saving ? t("common.saving") : t("common.save")}
            </GradientButton>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={!!pendingDelete} onOpenChange={(o) => { if (!o) setPendingDelete(null); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("aiAgent.slot.deleteTitle")}</DialogTitle>
            <DialogDescription>
              {t("aiAgent.slot.deleteConfirm", { name: pendingDelete?.name ?? "" })}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPendingDelete(null)}>
              {t("common.cancel")}
            </Button>
            <Button variant="destructive" onClick={onConfirmDelete} disabled={deleteMutation.isPending}>
              {deleteMutation.isPending ? t("common.deleting") : t("common.delete")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </LoadProvider>
  );
}
