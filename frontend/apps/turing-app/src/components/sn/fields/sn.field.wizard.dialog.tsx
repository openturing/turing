import * as React from "react";
import { useTranslation } from "react-i18next";
import {
  IconAlertTriangle,
  IconArrowLeft,
  IconFileUpload,
  IconSparkles,
  IconWand,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";

import {
  useDeriveManifest,
  useManifestDeriveAvailable,
  useProvisionManifest,
} from "@/api/queries/sn-manifest.queries";
import { useSnSite } from "@/api/queries/sn-site.queries";
import { BadgeFieldType } from "@/components/badge-field-type";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { GradientButton } from "@/components/ui/gradient-button";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import type { TurSNSiteManifest } from "@/models/sn/sn-manifest.model.ts";

type Props = {
  siteId: string;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** Field names already on the site — derived fields matching these are shown but excluded. */
  existingFieldNames: string[];
  /** Called after fields are successfully provisioned, so the list can refresh. */
  onApplied: () => void;
};

/**
 * Parses pasted/uploaded text into a list of sample documents. Accepts a JSON
 * array, a single JSON object, or newline-delimited JSON (one object per line).
 */
function parseDocuments(raw: string): Record<string, unknown>[] {
  const trimmed = raw.trim();
  if (!trimmed) {
    throw new Error("empty");
  }
  const isObject = (v: unknown): v is Record<string, unknown> =>
    typeof v === "object" && v !== null && !Array.isArray(v);

  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed);
  } catch {
    // Fall back to NDJSON (one JSON object per line).
    const lines = trimmed.split("\n").map((l) => l.trim()).filter(Boolean);
    const docs = lines.map((l) => JSON.parse(l));
    const objects = docs.filter(isObject);
    if (objects.length === 0) {
      throw new Error("noObjects");
    }
    return objects;
  }

  const arr = Array.isArray(parsed) ? parsed : [parsed];
  const objects = arr.filter(isObject);
  if (objects.length === 0) {
    throw new Error("noObjects");
  }
  return objects;
}

/**
 * T387 — "Generate fields from sample documents" wizard. Two steps:
 *   1. paste/upload a sample of the source's documents;
 *   2. review the derived draft schema and apply the chosen fields.
 *
 * Derivation calls {@code POST /api/sn/manifest/derive} (a draft, never applied);
 * applying converges the chosen fields into the live site via the idempotent
 * {@code POST /api/sn/manifest}. Fields that already exist are shown but excluded.
 */
export const SNFieldWizardDialog: React.FC<Props> = ({
  siteId,
  open,
  onOpenChange,
  existingFieldNames,
  onApplied,
}) => {
  const { t } = useTranslation();

  const { data: site } = useSnSite(open ? siteId : undefined);
  const { data: llmAvailable } = useManifestDeriveAvailable(open);
  const deriveMutation = useDeriveManifest();
  const provisionMutation = useProvisionManifest();

  const [step, setStep] = React.useState<1 | 2>(1);
  const [rawJson, setRawJson] = React.useState("");
  const [inputError, setInputError] = React.useState<string | null>(null);
  const [draft, setDraft] = React.useState<TurSNSiteManifest | null>(null);
  const [selected, setSelected] = React.useState<Set<string>>(new Set());
  const fileInputRef = React.useRef<HTMLInputElement>(null);

  const existing = React.useMemo(
    () => new Set(existingFieldNames.map((n) => n.toLowerCase())),
    [existingFieldNames],
  );

  const reset = React.useCallback(() => {
    setStep(1);
    setRawJson("");
    setInputError(null);
    setDraft(null);
    setSelected(new Set());
    deriveMutation.reset();
    provisionMutation.reset();
  }, [deriveMutation, provisionMutation]);

  const handleOpenChange = React.useCallback(
    (value: boolean) => {
      onOpenChange(value);
      if (!value) {
        reset();
      }
    },
    [onOpenChange, reset],
  );

  const handleFile = React.useCallback(async (file: File | undefined) => {
    if (!file) {
      return;
    }
    const text = await file.text();
    setRawJson(text);
    setInputError(null);
  }, []);

  const handleAnalyze = React.useCallback(() => {
    setInputError(null);
    let documents: Record<string, unknown>[];
    try {
      documents = parseDocuments(rawJson);
    } catch {
      setInputError(t("sn.fields.wizard.invalidJson"));
      return;
    }
    deriveMutation.mutate(
      {
        name: site?.name ?? "",
        seInstanceId: site?.turSEInstance?.id,
        documents,
      },
      {
        onSuccess: (result) => {
          setDraft(result);
          // Pre-select every derived field that the site doesn't already have.
          setSelected(
            new Set(
              (result.fields ?? [])
                .map((f) => f.name)
                .filter((name) => !existing.has(name.toLowerCase())),
            ),
          );
          setStep(2);
        },
        onError: () => setInputError(t("sn.fields.wizard.deriveError")),
      },
    );
  }, [rawJson, site, deriveMutation, existing, t]);

  const toggleField = React.useCallback((name: string, checked: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) {
        next.add(name);
      } else {
        next.delete(name);
      }
      return next;
    });
  }, []);

  const handleApply = React.useCallback(() => {
    if (!draft || selected.size === 0) {
      return;
    }
    const manifest: TurSNSiteManifest = {
      name: site?.name ?? draft.name,
      seInstanceId: site?.turSEInstance?.id ?? draft.seInstanceId,
      schemaVersion: draft.schemaVersion,
      fields: draft.fields.filter((f) => selected.has(f.name)),
    };
    provisionMutation.mutate(manifest, {
      onSuccess: (result) => {
        toast.success(
          t("sn.fields.wizard.applied", {
            created: result.fieldsCreated.length,
            skipped: result.fieldsSkipped.length,
          }),
        );
        onApplied();
        handleOpenChange(false);
      },
      onError: () => toast.error(t("sn.fields.wizard.applyError")),
    });
  }, [draft, selected, site, provisionMutation, t, onApplied, handleOpenChange]);

  const derivedFields = draft?.fields ?? [];

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <div className="mx-auto mb-2 flex h-12 w-12 items-center justify-center rounded-full bg-linear-to-br from-blue-500/10 to-indigo-500/10 ring-1 ring-blue-500/20">
            <IconSparkles className="size-6 text-blue-600 dark:text-blue-400" />
          </div>
          <DialogTitle className="text-center">{t("sn.fields.wizard.title")}</DialogTitle>
          <DialogDescription className="text-center">
            {step === 1
              ? t("sn.fields.wizard.step1Description")
              : t("sn.fields.wizard.step2Description")}
          </DialogDescription>
        </DialogHeader>

        {step === 1 && (
          <div className="space-y-3">
            <Textarea
              value={rawJson}
              onChange={(e) => setRawJson(e.target.value)}
              placeholder={t("sn.fields.wizard.jsonPlaceholder")}
              className="h-48 font-mono text-xs"
            />
            <div className="flex items-center justify-between gap-2">
              <GradientButton
                type="button"
                variant="outline"
                size="sm"
                onClick={() => fileInputRef.current?.click()}
              >
                <IconFileUpload className="size-4" />
                {t("sn.fields.wizard.uploadFile")}
              </GradientButton>
              <input
                ref={fileInputRef}
                type="file"
                accept=".json,application/json,.ndjson,.jsonl"
                aria-label={t("sn.fields.wizard.uploadFile")}
                title={t("sn.fields.wizard.uploadFile")}
                className="hidden"
                onChange={(e) => void handleFile(e.target.files?.[0])}
              />
              {llmAvailable === false && (
                <span className="text-xs text-muted-foreground">
                  {t("sn.fields.wizard.heuristicOnly")}
                </span>
              )}
            </div>
            {inputError && (
              <div className="flex items-center gap-2 rounded-md bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-400">
                <IconAlertTriangle className="size-4 shrink-0" />
                {inputError}
              </div>
            )}
          </div>
        )}

        {step === 2 && (
          <div className="space-y-3">
            <div className="max-h-72 overflow-auto rounded-md border">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-10"></TableHead>
                    <TableHead>{t("sn.fields.wizard.colName")}</TableHead>
                    <TableHead>{t("sn.fields.wizard.colType")}</TableHead>
                    <TableHead className="text-center">{t("sn.fields.wizard.colFacet")}</TableHead>
                    <TableHead className="text-center">{t("sn.fields.wizard.colMulti")}</TableHead>
                    <TableHead className="text-center">{t("sn.fields.wizard.colRequired")}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {derivedFields.map((field) => {
                    const alreadyExists = existing.has(field.name.toLowerCase());
                    return (
                      <TableRow key={field.name} className={alreadyExists ? "opacity-50" : ""}>
                        <TableCell>
                          <Checkbox
                            checked={selected.has(field.name)}
                            disabled={alreadyExists}
                            onCheckedChange={(c) => toggleField(field.name, c === true)}
                          />
                        </TableCell>
                        <TableCell className="font-mono text-xs">
                          {field.name}
                          {alreadyExists && (
                            <Badge variant="outline" className="ml-2 text-[10px]">
                              {t("sn.fields.wizard.exists")}
                            </Badge>
                          )}
                        </TableCell>
                        <TableCell>
                          <BadgeFieldType type={field.type} />
                        </TableCell>
                        <TableCell className="text-center">{field.facet ? "✓" : "—"}</TableCell>
                        <TableCell className="text-center">{field.multiValued ? "✓" : "—"}</TableCell>
                        <TableCell className="text-center">{field.mandatory ? "✓" : "—"}</TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
            <p className="text-xs text-muted-foreground">
              {t("sn.fields.wizard.selectedCount", { count: selected.size })}
            </p>
          </div>
        )}

        <DialogFooter className="gap-2 sm:gap-2">
          {step === 1 && (
            <GradientButton
              onClick={handleAnalyze}
              disabled={!rawJson.trim() || deriveMutation.isPending || !site}
            >
              <IconWand className="size-4" />
              {deriveMutation.isPending
                ? t("sn.fields.wizard.analyzing")
                : t("sn.fields.wizard.analyze")}
            </GradientButton>
          )}
          {step === 2 && (
            <>
              <GradientButton variant="ghost" onClick={() => setStep(1)}>
                <IconArrowLeft className="size-4" />
                {t("sn.fields.wizard.back")}
              </GradientButton>
              <GradientButton
                onClick={handleApply}
                disabled={selected.size === 0 || provisionMutation.isPending}
              >
                {provisionMutation.isPending
                  ? t("sn.fields.wizard.applying")
                  : t("sn.fields.wizard.apply", { count: selected.size })}
              </GradientButton>
            </>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
