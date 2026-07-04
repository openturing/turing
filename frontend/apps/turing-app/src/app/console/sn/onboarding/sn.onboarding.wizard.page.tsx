import * as React from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { useQueryClient } from "@tanstack/react-query";
import {
  IconAlertTriangle,
  IconArrowLeft,
  IconCheckupList,
  IconCircleCheck,
  IconExternalLink,
  IconFileUpload,
  IconRocket,
  IconSettings,
  IconSparkles,
  IconWand,
} from "@tabler/icons-react";
import { toast } from "@viglet/viglet-design-system";

import {
  useDeriveManifest,
  useManifestDeriveAvailable,
  useProvisionManifest,
} from "@/api/queries/sn-manifest.queries";
import {
  useNLFacetEvalAvailable,
  useRunNLFacetEval,
} from "@/api/queries/sn-nl-facet-eval.queries";
import { useSeInstances } from "@/api/queries/se-instance.queries";
import { queryKeys } from "@/api/queries/keys";
import { ROUTES } from "@/app/routes.const";
import { BadgeFieldType } from "@/components/badge-field-type";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
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
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Textarea } from "@/components/ui/textarea";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import type { TurNLFacetEvalReport } from "@/models/sn/sn-nl-facet-eval.model.ts";
import type {
  TurSNManifestFieldSpec,
  TurSNManifestResult,
  TurSNSiteManifest,
} from "@/models/sn/sn-manifest.model.ts";

type Step = 1 | 2 | 3 | 4;

/**
 * Parses pasted/uploaded text into sample documents. Accepts a JSON array, a
 * single JSON object, or newline-delimited JSON (one object per line).
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
    const lines = trimmed.split("\n").map((l) => l.trim()).filter(Boolean);
    const objects = lines.map((l) => JSON.parse(l)).filter(isObject);
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

const NUMERIC_TYPES = new Set(["INT", "LONG", "FLOAT", "DOUBLE", "CURRENCY"]);

/**
 * Builds a starter NL→facet eval pack (T385) from the provisioned schema so the
 * operator has a runnable example to edit instead of a blank textarea. Picks a
 * facetable string field for a term-filter case and a numeric field for a range
 * case; `fields` is left empty so the backend resolves the live site schema.
 */
function buildStarterPack(
  siteName: string,
  fields: TurSNManifestFieldSpec[],
  locale: string | null,
): string {
  const facetField = fields.find(
    (f) => f.facet && (f.type === "STRING" || f.type === "ARRAY"),
  );
  const numericField = fields.find((f) => NUMERIC_TYPES.has(f.type));

  const cases: unknown[] = [];
  if (facetField) {
    cases.push({
      name: `filter by ${facetField.name}`,
      query: `documents where ${facetField.name} is <some value>`,
      expect: {
        filters: [{ field: facetField.name, value: "<some value>" }],
        ranges: [],
        sort: null,
      },
    });
  }
  if (numericField) {
    cases.push({
      name: `${numericField.name} upper bound`,
      query: `documents with ${numericField.name} under 1000`,
      expect: {
        filters: [],
        ranges: [{ field: numericField.name, lte: 1000 }],
        sort: null,
      },
    });
  }
  if (cases.length === 0) {
    cases.push({
      name: "example",
      query: "describe what a user might type here",
      expect: { filters: [], ranges: [], sort: null },
    });
  }

  return JSON.stringify(
    {
      name: `${siteName} smoke test`,
      index: siteName,
      locale,
      fields: [],
      cases,
    },
    null,
    2,
  );
}

/**
 * T391 — Structured-source onboarding wizard. The capstone flow that chains the
 * Block-R indexing capabilities into one guided operation a non-engineer can run:
 *
 *   1. point at a sample of the source's documents (+ identity & search engine);
 *   2. review the schema {@code POST /api/sn/manifest/derive} (T387) inferred;
 *   3. create the catalog via the idempotent {@code POST /api/sn/manifest} (T382);
 *   4. validate the schema is queryable with an NL→facet eval pack (T385); go live.
 *
 * Nothing is created until step 2's "Create catalog"; the eval step is optional.
 */
/**
 * @param baseRoute Base SN instance route the "Configure site" / "Add fields"
 *   completion actions navigate to. Defaults to the console
 *   (`ROUTES.SN_INSTANCE`); the Bento onboarding page (T559) passes
 *   `ROUTES.BENTO_SN_INSTANCE` so the wizard lands the user inside the shell.
 */
export default function SNOnboardingWizardPage({ baseRoute = ROUTES.SN_INSTANCE }: Readonly<{ baseRoute?: string }> = {}) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  useSubPageBreadcrumb(t("sn.onboarding.title"));

  const { data: seInstances } = useSeInstances();
  const { data: llmAvailable } = useManifestDeriveAvailable();
  const { data: evalAvailable } = useNLFacetEvalAvailable();
  const deriveMutation = useDeriveManifest();
  const provisionMutation = useProvisionManifest();
  const evalMutation = useRunNLFacetEval();

  const [step, setStep] = React.useState<Step>(1);

  // Step 1 — identity + sample.
  const [name, setName] = React.useState("");
  const [description, setDescription] = React.useState("");
  const [seInstanceId, setSeInstanceId] = React.useState<string>("");
  const [localesInput, setLocalesInput] = React.useState("");
  const [rawJson, setRawJson] = React.useState("");
  const [inputError, setInputError] = React.useState<string | null>(null);
  const fileInputRef = React.useRef<HTMLInputElement>(null);

  // Step 2 — derived schema review.
  const [draft, setDraft] = React.useState<TurSNSiteManifest | null>(null);
  const [selected, setSelected] = React.useState<Set<string>>(new Set());

  // Step 3 — provision result + eval.
  const [provisionResult, setProvisionResult] =
    React.useState<TurSNManifestResult | null>(null);
  const [evalPackJson, setEvalPackJson] = React.useState("");
  const [evalReport, setEvalReport] = React.useState<TurNLFacetEvalReport | null>(null);
  const [evalError, setEvalError] = React.useState<string | null>(null);

  const enabledInstances = React.useMemo(
    () => (Array.isArray(seInstances) ? seInstances : []).filter((se) => se.enabled === 1),
    [seInstances],
  );

  const parsedLocales = React.useCallback(
    () =>
      localesInput
        .split(",")
        .map((l) => l.trim())
        .filter(Boolean),
    [localesInput],
  );

  const handleFile = React.useCallback(async (file: File | undefined) => {
    if (!file) {
      return;
    }
    setRawJson(await file.text());
    setInputError(null);
  }, []);

  const handleDerive = React.useCallback(() => {
    setInputError(null);
    let documents: Record<string, unknown>[];
    try {
      documents = parseDocuments(rawJson);
    } catch {
      setInputError(t("sn.onboarding.invalidJson"));
      return;
    }
    const locales = parsedLocales();
    deriveMutation.mutate(
      {
        name: name.trim(),
        description: description.trim() || undefined,
        seInstanceId,
        locales: locales.length > 0 ? locales : undefined,
        documents,
      },
      {
        onSuccess: (result) => {
          setDraft(result);
          setSelected(new Set((result.fields ?? []).map((f) => f.name)));
          setStep(2);
        },
        onError: () => setInputError(t("sn.onboarding.deriveError")),
      },
    );
  }, [rawJson, name, description, seInstanceId, parsedLocales, deriveMutation, t]);

  const toggleField = React.useCallback((fieldName: string, checked: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (checked) {
        next.add(fieldName);
      } else {
        next.delete(fieldName);
      }
      return next;
    });
  }, []);

  const handleProvision = React.useCallback(() => {
    if (!draft || selected.size === 0) {
      return;
    }
    const fields = draft.fields.filter((f) => selected.has(f.name));
    const locales = parsedLocales();
    const manifest: TurSNSiteManifest = {
      name: name.trim() || draft.name,
      description: description.trim() || draft.description,
      seInstanceId,
      schemaVersion: draft.schemaVersion,
      locales: locales.length > 0 ? locales : undefined,
      fields,
    };
    provisionMutation.mutate(manifest, {
      onSuccess: (result) => {
        setProvisionResult(result);
        queryClient.invalidateQueries({ queryKey: queryKeys.snSites.all() });
        setEvalPackJson(buildStarterPack(result.siteName, fields, locales[0] ?? null));
        setEvalReport(null);
        setEvalError(null);
        toast.success(
          t("sn.onboarding.provisioned", {
            name: result.siteName,
            count: result.fieldsCreated.length,
          }),
        );
        setStep(3);
      },
      onError: () => toast.error(t("sn.onboarding.provisionError")),
    });
  }, [
    draft,
    selected,
    name,
    description,
    seInstanceId,
    parsedLocales,
    provisionMutation,
    queryClient,
    t,
  ]);

  const handleRunEval = React.useCallback(() => {
    setEvalError(null);
    let pack: unknown;
    try {
      pack = JSON.parse(evalPackJson);
    } catch {
      setEvalError(t("sn.onboarding.evalInvalidJson"));
      return;
    }
    evalMutation.mutate(pack as never, {
      onSuccess: (report) => setEvalReport(report),
      onError: () => setEvalError(t("sn.onboarding.evalError")),
    });
  }, [evalPackJson, evalMutation, t]);

  const resetAll = React.useCallback(() => {
    setStep(1);
    setName("");
    setDescription("");
    setSeInstanceId("");
    setLocalesInput("");
    setRawJson("");
    setInputError(null);
    setDraft(null);
    setSelected(new Set());
    setProvisionResult(null);
    setEvalPackJson("");
    setEvalReport(null);
    setEvalError(null);
    deriveMutation.reset();
    provisionMutation.reset();
    evalMutation.reset();
  }, [deriveMutation, provisionMutation, evalMutation]);

  const stepLabels = [
    t("sn.onboarding.steps.sample"),
    t("sn.onboarding.steps.review"),
    t("sn.onboarding.steps.validate"),
    t("sn.onboarding.steps.live"),
  ];

  const canDerive =
    Boolean(name.trim()) &&
    Boolean(seInstanceId) &&
    Boolean(rawJson.trim()) &&
    !deriveMutation.isPending;

  const derivedFields = draft?.fields ?? [];

  return (
    <>
      <div className="mx-auto w-full max-w-3xl space-y-6 px-4 lg:px-6">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-linear-to-br from-blue-500/10 to-indigo-500/10 ring-1 ring-blue-500/10 dark:from-blue-500/20 dark:to-indigo-500/20">
            <IconRocket className="size-5 text-blue-600 dark:text-blue-400" />
          </div>
          <div className="min-w-0">
            <h1 className="text-base font-semibold leading-tight text-foreground">
              {t("sn.onboarding.feature")}
            </h1>
            <p className="text-xs text-muted-foreground">{t("sn.onboarding.description")}</p>
          </div>
        </div>

        {/* Step indicator */}
        <nav className="flex items-center justify-between">
          {stepLabels.map((label, i) => {
            const index = (i + 1) as Step;
            const isDone = step > index;
            const isCurrent = step === index;
            return (
              <div key={label} className="flex flex-1 items-center">
                <div className="flex items-center gap-2">
                  <span
                    className={`flex h-7 w-7 items-center justify-center rounded-full text-xs font-bold transition-colors ${
                      isDone
                        ? "bg-emerald-500 text-white"
                        : isCurrent
                          ? "bg-linear-to-br from-blue-600 to-indigo-600 text-white"
                          : "bg-muted text-muted-foreground"
                    }`}
                  >
                    {isDone ? <IconCircleCheck className="size-4" /> : index}
                  </span>
                  <span
                    className={`hidden text-xs font-medium sm:inline ${
                      isCurrent ? "text-foreground" : "text-muted-foreground"
                    }`}
                  >
                    {label}
                  </span>
                </div>
                {i < stepLabels.length - 1 && (
                  <div className="mx-2 h-px flex-1 bg-border" />
                )}
              </div>
            );
          })}
        </nav>

        {/* ── Step 1 — sample & identity ── */}
        {step === 1 && (
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <IconSparkles className="size-5 text-blue-600 dark:text-blue-400" />
                {t("sn.onboarding.step1Title")}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-2">
                <Label htmlFor="catalog-name">{t("sn.onboarding.nameLabel")}</Label>
                <Input
                  id="catalog-name"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder={t("sn.onboarding.namePlaceholder")}
                />
              </div>
              <div className="grid gap-2">
                <Label htmlFor="catalog-description">
                  {t("sn.onboarding.descriptionLabel")}
                </Label>
                <Input
                  id="catalog-description"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder={t("sn.onboarding.descriptionPlaceholder")}
                />
              </div>
              <div className="grid gap-2 sm:grid-cols-2 sm:gap-4">
                <div className="grid gap-2">
                  <Label>{t("sn.onboarding.seInstanceLabel")}</Label>
                  <Select value={seInstanceId} onValueChange={setSeInstanceId}>
                    <SelectTrigger>
                      <SelectValue placeholder={t("sn.onboarding.seInstancePlaceholder")} />
                    </SelectTrigger>
                    <SelectContent>
                      {enabledInstances.map((se) => (
                        <SelectItem key={se.id} value={se.id}>
                          {se.title}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="grid gap-2">
                  <Label htmlFor="catalog-locales">
                    {t("sn.onboarding.localesLabel")}
                  </Label>
                  <Input
                    id="catalog-locales"
                    value={localesInput}
                    onChange={(e) => setLocalesInput(e.target.value)}
                    placeholder={t("sn.onboarding.localesPlaceholder")}
                  />
                </div>
              </div>
              <div className="grid gap-2">
                <Label htmlFor="catalog-sample">{t("sn.onboarding.sampleLabel")}</Label>
                <Textarea
                  id="catalog-sample"
                  value={rawJson}
                  onChange={(e) => setRawJson(e.target.value)}
                  placeholder={t("sn.onboarding.samplePlaceholder")}
                  className="h-44 font-mono text-xs"
                />
                <div className="flex items-center justify-between gap-2">
                  <GradientButton
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => fileInputRef.current?.click()}
                  >
                    <IconFileUpload className="size-4" />
                    {t("sn.onboarding.uploadFile")}
                  </GradientButton>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept=".json,application/json,.ndjson,.jsonl"
                    aria-label={t("sn.onboarding.uploadFile")}
                    title={t("sn.onboarding.uploadFile")}
                    className="hidden"
                    onChange={(e) => void handleFile(e.target.files?.[0])}
                  />
                  {llmAvailable === false && (
                    <span className="text-xs text-muted-foreground">
                      {t("sn.onboarding.heuristicOnly")}
                    </span>
                  )}
                </div>
              </div>
              {inputError && (
                <div className="flex items-center gap-2 rounded-md bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-400">
                  <IconAlertTriangle className="size-4 shrink-0" />
                  {inputError}
                </div>
              )}
              <div className="flex justify-end">
                <GradientButton onClick={handleDerive} disabled={!canDerive}>
                  <IconWand className="size-4" />
                  {deriveMutation.isPending
                    ? t("sn.onboarding.analyzing")
                    : t("sn.onboarding.analyze")}
                </GradientButton>
              </div>
            </CardContent>
          </Card>
        )}

        {/* ── Step 2 — review schema ── */}
        {step === 2 && (
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <IconCheckupList className="size-5 text-blue-600 dark:text-blue-400" />
                {t("sn.onboarding.step2Title")}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <p className="text-sm text-muted-foreground">
                {t("sn.onboarding.step2Description")}
              </p>
              <div className="max-h-80 overflow-auto rounded-md border">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-10"></TableHead>
                      <TableHead>{t("sn.onboarding.colName")}</TableHead>
                      <TableHead>{t("sn.onboarding.colType")}</TableHead>
                      <TableHead className="text-center">
                        {t("sn.onboarding.colFacet")}
                      </TableHead>
                      <TableHead className="text-center">
                        {t("sn.onboarding.colMulti")}
                      </TableHead>
                      <TableHead className="text-center">
                        {t("sn.onboarding.colRequired")}
                      </TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {derivedFields.map((field) => (
                      <TableRow key={field.name}>
                        <TableCell>
                          <Checkbox
                            checked={selected.has(field.name)}
                            onCheckedChange={(c) => toggleField(field.name, c === true)}
                          />
                        </TableCell>
                        <TableCell className="font-mono text-xs">{field.name}</TableCell>
                        <TableCell>
                          <BadgeFieldType type={field.type} />
                        </TableCell>
                        <TableCell className="text-center">
                          {field.facet ? "✓" : "—"}
                        </TableCell>
                        <TableCell className="text-center">
                          {field.multiValued ? "✓" : "—"}
                        </TableCell>
                        <TableCell className="text-center">
                          {field.mandatory ? "✓" : "—"}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
              <p className="text-xs text-muted-foreground">
                {t("sn.onboarding.selectedCount", { count: selected.size })}
              </p>
              <div className="flex justify-between gap-2">
                <GradientButton variant="ghost" onClick={() => setStep(1)}>
                  <IconArrowLeft className="size-4" />
                  {t("sn.onboarding.back")}
                </GradientButton>
                <GradientButton
                  onClick={handleProvision}
                  disabled={selected.size === 0 || provisionMutation.isPending}
                >
                  <IconRocket className="size-4" />
                  {provisionMutation.isPending
                    ? t("sn.onboarding.provisioning")
                    : t("sn.onboarding.provision", { count: selected.size })}
                </GradientButton>
              </div>
            </CardContent>
          </Card>
        )}

        {/* ── Step 3 — validate (NL→facet eval) ── */}
        {step === 3 && (
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <IconCheckupList className="size-5 text-blue-600 dark:text-blue-400" />
                {t("sn.onboarding.step3Title")}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <p className="text-sm text-muted-foreground">
                {t("sn.onboarding.step3Description")}
              </p>
              {evalAvailable === false ? (
                <div className="flex items-center gap-2 rounded-md bg-amber-500/10 px-3 py-2 text-sm text-amber-700 dark:text-amber-400">
                  <IconAlertTriangle className="size-4 shrink-0" />
                  {t("sn.onboarding.evalUnavailable")}
                </div>
              ) : (
                <>
                  <Textarea
                    value={evalPackJson}
                    onChange={(e) => setEvalPackJson(e.target.value)}
                    className="h-56 font-mono text-xs"
                    aria-label={t("sn.onboarding.evalPackLabel")}
                  />
                  {evalError && (
                    <div className="flex items-center gap-2 rounded-md bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-400">
                      <IconAlertTriangle className="size-4 shrink-0" />
                      {evalError}
                    </div>
                  )}
                  {evalReport && <EvalReportView report={evalReport} t={t} />}
                  <div>
                    <GradientButton
                      variant="outline"
                      onClick={handleRunEval}
                      disabled={!evalPackJson.trim() || evalMutation.isPending}
                    >
                      <IconCheckupList className="size-4" />
                      {evalMutation.isPending
                        ? t("sn.onboarding.evalRunning")
                        : t("sn.onboarding.runEval")}
                    </GradientButton>
                  </div>
                </>
              )}
              <div className="flex justify-end gap-2 border-t pt-3">
                <GradientButton onClick={() => setStep(4)}>
                  {t("sn.onboarding.continue")}
                </GradientButton>
              </div>
            </CardContent>
          </Card>
        )}

        {/* ── Step 4 — go live ── */}
        {step === 4 && provisionResult && (
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <IconCircleCheck className="size-5 text-emerald-600 dark:text-emerald-400" />
                {t("sn.onboarding.step4Title")}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <p className="text-sm text-muted-foreground">
                {t("sn.onboarding.step4Description", { name: provisionResult.siteName })}
              </p>
              <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">
                <StatCard
                  label={t("sn.onboarding.statFieldsCreated")}
                  value={provisionResult.fieldsCreated.length}
                />
                <StatCard
                  label={t("sn.onboarding.statFieldsSkipped")}
                  value={provisionResult.fieldsSkipped.length}
                />
                <StatCard
                  label={t("sn.onboarding.statLocales")}
                  value={provisionResult.localesCreated.length}
                />
              </div>
              <div className="flex flex-wrap gap-2">
                <GradientButton
                  onClick={() =>
                    navigate(`${baseRoute}/${provisionResult.siteId}`)
                  }
                >
                  <IconSettings className="size-4" />
                  {t("sn.onboarding.configureSite")}
                </GradientButton>
                <GradientButton
                  variant="outline"
                  onClick={() =>
                    globalThis.open(`/sn/${provisionResult.siteName}`, "_blank")
                  }
                >
                  <IconExternalLink className="size-4" />
                  {t("sn.onboarding.openSearch")}
                </GradientButton>
                <GradientButton
                  variant="outline"
                  onClick={() =>
                    navigate(`${baseRoute}/${provisionResult.siteId}/field`)
                  }
                >
                  {t("sn.onboarding.addFields")}
                </GradientButton>
                <GradientButton variant="ghost" onClick={resetAll}>
                  {t("sn.onboarding.onboardAnother")}
                </GradientButton>
              </div>
            </CardContent>
          </Card>
        )}
      </div>
    </>
  );
}

const StatCard: React.FC<{ label: string; value: number }> = ({ label, value }) => (
  <div className="rounded-lg border bg-muted/30 p-3 text-center">
    <div className="text-2xl font-bold text-foreground">{value}</div>
    <div className="text-xs text-muted-foreground">{label}</div>
  </div>
);

const EvalReportView: React.FC<{
  report: TurNLFacetEvalReport;
  t: (key: string, opts?: Record<string, unknown>) => string;
}> = ({ report, t }) => (
  <div className="space-y-2 rounded-md border p-3">
    <div className="flex items-center justify-between">
      <Badge
        variant="outline"
        className={
          report.passed
            ? "bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400"
            : "bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400"
        }
      >
        {report.passed ? t("sn.onboarding.evalPassed") : t("sn.onboarding.evalFailed")}
      </Badge>
      <span className="text-xs text-muted-foreground">
        {t("sn.onboarding.evalScore", {
          passed: report.passedCount,
          total: report.caseCount,
          score: Math.round(report.score * 100),
        })}
      </span>
    </div>
    {report.error && (
      <p className="text-xs text-red-600 dark:text-red-400">{report.error}</p>
    )}
    <ul className="space-y-1">
      {report.results.map((r) => (
        <li key={r.caseName} className="text-xs">
          <span className={r.passed ? "text-emerald-600 dark:text-emerald-400" : "text-red-600 dark:text-red-400"}>
            {r.passed ? "✓" : "✗"}
          </span>{" "}
          <span className="font-medium">{r.caseName}</span>
          {r.ungroundedFields.length > 0 && (
            <span className="ml-1 text-amber-600 dark:text-amber-400">
              {t("sn.onboarding.evalUngrounded", {
                fields: r.ungroundedFields.join(", "),
              })}
            </span>
          )}
          {r.findings.length > 0 && (
            <span className="ml-1 text-muted-foreground">— {r.findings.join("; ")}</span>
          )}
        </li>
      ))}
    </ul>
  </div>
);
