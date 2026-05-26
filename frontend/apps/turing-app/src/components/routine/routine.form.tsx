"use client";
import { ROUTES } from "@/app/routes.const";
import {
  useCreateRoutine,
  useDeleteRoutine,
  useUpdateRoutine,
} from "@/api/queries/routine.queries";
import { StickyPageHeader } from "@/components/sticky-page-header";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { GradientButton } from "@/components/ui/gradient-button";
import { GradientSwitch } from "@/components/ui/gradient-switch";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { SectionCard } from "@/components/ui/section-card";
import { Textarea } from "@/components/ui/textarea";
import type { TurRoutine, TurRoutineKind } from "@/models/genai/routine.model";
import {
  IconClock,
  IconCode,
  IconDeviceFloppy,
  IconSettings,
  IconTrash,
  IconX,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import { DialogDelete } from "@/components/dialog.delete";

const KINDS: TurRoutineKind[] = ["NATIVE", "GROOVY"];

interface Props {
  readonly value: TurRoutine;
  readonly isNew: boolean;
}

/**
 * T48 — edit/create form for a {@code TurRoutine}. Native routines bind
 * a Spring AI {@code @Tool} method by name; Groovy routines carry a
 * script body executed inline by the JMS worker.
 *
 * @since 2026.3.1
 */
export const RoutineForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const form = useForm<TurRoutine>({ defaultValues: value });
  const navigate = useNavigate();
  const [deleteOpen, setDeleteOpen] = useState(false);

  const createMutation = useCreateRoutine();
  const updateMutation = useUpdateRoutine();
  const deleteMutation = useDeleteRoutine();

  useEffect(() => {
    form.reset(value);
  }, [value, form]);

  const watchedKind = form.watch("kind");

  async function onSubmit(data: TurRoutine) {
    const payload: TurRoutine = {
      ...data,
      // Strip the inactive-branch field so we don't keep stale state in
      // the row when an admin switches NATIVE ↔ GROOVY mid-edit.
      nativeToolName: data.kind === "NATIVE" ? data.nativeToolName : undefined,
      groovyScript: data.kind === "GROOVY" ? data.groovyScript : undefined,
    };
    try {
      const saved = isNew
        ? await createMutation.mutateAsync(payload)
        : await updateMutation.mutateAsync(payload);
      if (saved) {
        toast.success(
          isNew
            ? t("routine.created", { name: saved.name })
            : t("routine.updated", { name: saved.name }),
        );
        navigate(ROUTES.ROUTINE_INSTANCE);
      } else {
        toast.error(t("routine.saveFailed"));
      }
    } catch (err) {
      console.error("Routine save failed", err);
      toast.error(t("routine.saveFailed"));
    }
  }

  async function onDelete() {
    if (!value.id) return;
    try {
      const ok = await deleteMutation.mutateAsync(value);
      if (ok) {
        toast.success(t("routine.deleted", { name: value.name }));
        navigate(ROUTES.ROUTINE_INSTANCE);
      } else {
        toast.error(t("routine.deleteFailed"));
      }
    } catch (err) {
      console.error("Routine delete failed", err);
      toast.error(t("routine.deleteFailed"));
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
        <StickyPageHeader>
          <StickyPageHeader.Title
            icon={IconClock}
            feature={isNew ? t("routine.newRoutine") : value.name}
            description={isNew ? t("routine.createDescription") : t("routine.editDescription")}
          />
          <StickyPageHeader.Actions>
            <GradientButton type="submit" size="sm">
              <IconDeviceFloppy className="size-4" />
              {t("forms.formActions.saveChanges")}
            </GradientButton>
            <GradientButton
              type="button"
              variant="outline"
              size="sm"
              onClick={() => navigate(ROUTES.ROUTINE_INSTANCE)}
            >
              <IconX className="size-4" />
              {t("forms.formActions.cancel")}
            </GradientButton>
            {!isNew && (
              <GradientButton
                type="button"
                variant="destructive"
                size="sm"
                onClick={() => setDeleteOpen(true)}
              >
                <IconTrash className="size-4" />
                {t("forms.formActions.delete")}
              </GradientButton>
            )}
          </StickyPageHeader.Actions>
        </StickyPageHeader>

        <SectionCard variant="blue">
          <SectionCard.Header
            icon={IconClock}
            title={t("routine.details")}
            description={t("routine.detailsDesc")}
          />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="name"
              rules={{ required: t("routine.nameRequired") }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("routine.name")}</FormLabel>
                  <FormDescription>{t("routine.nameDesc")}</FormDescription>
                  <FormControl>
                    <Input {...field} placeholder="generate_proposal_pdf" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem className="mt-4">
                  <FormLabel>{t("routine.description")}</FormLabel>
                  <FormControl>
                    <Input
                      {...field}
                      value={field.value ?? ""}
                      placeholder={t("routine.descriptionPlaceholder")}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </SectionCard.Content>
        </SectionCard>

        <SectionCard variant="emerald">
          <SectionCard.Header
            icon={IconSettings}
            title={t("routine.execution")}
            description={t("routine.executionDesc")}
          />
          <SectionCard.Content>
            <FormField
              control={form.control}
              name="kind"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>{t("routine.kind")}</FormLabel>
                  <FormDescription>{t("routine.kindDesc")}</FormDescription>
                  <FormControl>
                    <Select
                      value={field.value ?? "NATIVE"}
                      onValueChange={(v) => field.onChange(v as TurRoutineKind)}
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {KINDS.map((k) => (
                          <SelectItem key={k} value={k}>
                            {t(`routine.kindOptions.${k}`)}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            {watchedKind === "NATIVE" && (
              <FormField
                control={form.control}
                name="nativeToolName"
                rules={{ required: t("routine.nativeToolNameRequired") }}
                render={({ field }) => (
                  <FormItem className="mt-4">
                    <FormLabel>{t("routine.nativeToolName")}</FormLabel>
                    <FormDescription>{t("routine.nativeToolNameDesc")}</FormDescription>
                    <FormControl>
                      <Input
                        {...field}
                        value={field.value ?? ""}
                        placeholder="get_current_time"
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            )}

            {watchedKind === "GROOVY" && (
              <FormField
                control={form.control}
                name="groovyScript"
                rules={{ required: t("routine.groovyScriptRequired") }}
                render={({ field }) => (
                  <FormItem className="mt-4">
                    <FormLabel className="flex items-center gap-2">
                      <IconCode className="size-4" />
                      {t("routine.groovyScript")}
                    </FormLabel>
                    <FormDescription>{t("routine.groovyScriptDesc")}</FormDescription>
                    <FormControl>
                      <Textarea
                        {...field}
                        value={field.value ?? ""}
                        rows={14}
                        spellCheck={false}
                        className="font-mono text-xs"
                        placeholder={`// Groovy. args, conversationId, routineName are bound.\n// Last expression is written to the scheduleAgent node's outputVariable.\nreturn "hello \${args.name ?: 'world'}"\n`}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            )}

            <FormField
              control={form.control}
              name="defaultTimeoutMs"
              render={({ field }) => (
                <FormItem className="mt-4">
                  <FormLabel>{t("routine.defaultTimeoutMs")}</FormLabel>
                  <FormDescription>{t("routine.defaultTimeoutMsDesc")}</FormDescription>
                  <FormControl>
                    <Input
                      type="number"
                      min={1000}
                      step={1000}
                      value={typeof field.value === "number" ? field.value : ""}
                      onChange={(e) => {
                        const v = e.target.value;
                        field.onChange(v === "" ? undefined : Number(v));
                      }}
                      placeholder="60000"
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <FormField
              control={form.control}
              name="enabled"
              render={({ field }) => (
                <FormItem className="mt-4 flex items-center justify-between rounded-lg border p-3">
                  <div>
                    <FormLabel className="text-sm">{t("routine.enabled")}</FormLabel>
                    <FormDescription>{t("routine.enabledDesc")}</FormDescription>
                  </div>
                  <FormControl>
                    <GradientSwitch
                      checked={field.value ?? true}
                      onCheckedChange={field.onChange}
                    />
                  </FormControl>
                </FormItem>
              )}
            />
          </SectionCard.Content>
        </SectionCard>

        {!isNew && (
          <DialogDelete
            open={deleteOpen}
            setOpen={setDeleteOpen}
            feature={t("routine.title")}
            name={value.name}
            onDelete={onDelete}
          />
        )}
      </form>
    </Form>
  );
};
