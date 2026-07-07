import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  IconChartHistogram,
  IconChecks,
  IconFlask,
  IconInbox,
  IconRobot,
  IconScale,
  IconSend,
  IconTargetArrow,
  IconUsers,
} from "@tabler/icons-react";

import { useAiAgents } from "@/api/queries/ai-agent.queries";
import {
  useEvalAgreement,
  useEvalCalibration,
  useEvalDatasets,
  useEvalReviewInbox,
  usePromoteReview,
  useSampleAudit,
  useSubmitReview,
} from "@/api/queries/eval-studio.queries";
import type {
  TurEvalReviewTask,
} from "@/models/eval/eval-studio.model";

/**
 * T593 / §XXXIII.8 — the human-review inbox, folded into the Eval Studio. Pick
 * an agent, read the transcript + expected summary of a parked task, submit a
 * {pass, score, notes} verdict (which merges back into the latest report), and
 * — closing the loop — promote the row into a golden dataset in one click.
 *
 * T594 / §XXXIII.9 adds calibration: a header panel with the inter-annotator
 * agreement (Fleiss' kappa) and the MODEL-vs-human calibration (Cohen's kappa),
 * a button to sample a MODEL-graded run into AUDIT tasks, N-reviewer progress
 * badges, and — for AUDIT tasks — the model's original verdict.
 *
 * @since 2026.3.4
 */
export function BentoEvalReviewInbox() {
  const { t } = useTranslation();
  const { data: agents } = useAiAgents();
  const [agentId, setAgentId] = useState<string>("");
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);

  const { data: tasks, isError } = useEvalReviewInbox(agentId || undefined);

  const selected = useMemo(
    () => (tasks ?? []).find((task) => task.id === selectedTaskId) ?? null,
    [tasks, selectedTaskId],
  );

  return (
    <div className="space-y-4">
      <div className="bento-tile bento-glass flex flex-wrap items-center gap-3 rounded-2xl border border-border/60 p-4">
        <IconRobot size={18} className="text-muted-foreground" />
        <label htmlFor="review-agent" className="text-sm text-muted-foreground">
          {t("evalStudio.review.agent", { defaultValue: "Agent" })}
        </label>
        <select
          id="review-agent"
          value={agentId}
          onChange={(event) => {
            setAgentId(event.target.value);
            setSelectedTaskId(null);
          }}
          className="min-w-56 rounded-lg border border-border/60 bg-card/60 px-3 py-1.5 text-sm"
        >
          <option value="">
            {t("evalStudio.review.pickAgent", { defaultValue: "Select an agent…" })}
          </option>
          {(agents ?? []).map((agent) => (
            <option key={agent.id} value={agent.id}>
              {agent.title}
            </option>
          ))}
        </select>
      </div>

      {agentId && <CalibrationPanel agentId={agentId} />}

      {isError && (
        <p className="text-sm text-rose-600 dark:text-rose-400">
          {t("common.connectionError", { resource: "review tasks" })}
        </p>
      )}

      {agentId && !isError && (tasks?.length ?? 0) === 0 && (
        <div className="bento-tile bento-glass rounded-2xl border border-border/60 p-8 text-center">
          <IconInbox size={28} className="mx-auto mb-3 text-muted-foreground" />
          <p className="font-medium">
            {t("evalStudio.review.empty", { defaultValue: "No pending reviews" })}
          </p>
          <p className="mt-1 text-sm text-muted-foreground">
            {t("evalStudio.review.emptyHint", {
              defaultValue:
                "Tasks appear here when an agent's human-review grader defers a case — or when you sample a MODEL-graded run for a calibration audit above.",
            })}
          </p>
        </div>
      )}

      {agentId && (tasks?.length ?? 0) > 0 && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-[280px_1fr]">
          <ul className="space-y-2">
            {(tasks ?? []).map((task) => {
              const isActive = task.id === selectedTaskId;
              const isAudit = task.taskType === "AUDIT";
              return (
                <li key={task.id}>
                  <button
                    type="button"
                    onClick={() => setSelectedTaskId(task.id)}
                    className={`bento-tile bento-tile-clickable w-full rounded-xl border p-3 text-left transition-colors ${
                      isActive
                        ? "border-primary/40 bg-primary/10"
                        : "border-border/60 bg-card/60 hover:bg-card"
                    }`}
                  >
                    <p className="truncate font-medium">
                      {task.caseName ??
                        t("evalStudio.review.unnamedCase", { defaultValue: "Unnamed case" })}
                    </p>
                    <div className="mt-1 flex flex-wrap items-center gap-1.5">
                      {isAudit && (
                        <span className="inline-flex items-center gap-1 rounded-full bg-amber-500/15 px-2 py-0.5 text-[11px] font-medium text-amber-700 dark:text-amber-300">
                          <IconFlask size={11} />
                          {t("evalStudio.review.auditBadge", { defaultValue: "audit" })}
                        </span>
                      )}
                      <span className="truncate text-xs text-muted-foreground">
                        {task.graderId}
                      </span>
                      {(task.requiredReviewers ?? 1) > 1 && (
                        <span className="inline-flex items-center gap-1 rounded-full border border-border/60 px-2 py-0.5 text-[11px] text-muted-foreground">
                          <IconUsers size={11} />
                          {task.verdictCount ?? 0}/{task.requiredReviewers}
                        </span>
                      )}
                    </div>
                  </button>
                </li>
              );
            })}
          </ul>

          {selected ? (
            <ReviewDetail agentId={agentId} task={selected} onDone={() => setSelectedTaskId(null)} />
          ) : (
            <div className="bento-tile bento-glass flex items-center justify-center rounded-2xl border border-border/60 p-8 text-sm text-muted-foreground">
              {t("evalStudio.review.selectHint", {
                defaultValue: "Select a task to review its transcript.",
              })}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function fmtKappa(kappa?: number | null): string {
  return kappa === null || kappa === undefined ? "—" : kappa.toFixed(2);
}

function CalibrationPanel({ agentId }: Readonly<{ agentId: string }>) {
  const { t } = useTranslation();
  const { data: agreement } = useEvalAgreement(agentId);
  const { data: calibration } = useEvalCalibration(agentId);
  const sample = useSampleAudit(agentId);

  const [sampleSize, setSampleSize] = useState<string>("10");
  const [reviewers, setReviewers] = useState<string>("1");

  return (
    <div className="bento-tile bento-glass space-y-4 rounded-2xl border border-border/60 p-4">
      <div className="flex flex-wrap items-center gap-2">
        <IconScale size={18} className="text-muted-foreground" />
        <h3 className="text-sm font-semibold">
          {t("evalStudio.calibration.title", { defaultValue: "Calibration & agreement" })}
        </h3>
      </div>

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div className="rounded-xl border border-border/60 bg-card/60 p-3">
          <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            <IconUsers size={13} />
            {t("evalStudio.calibration.agreement", { defaultValue: "Inter-annotator agreement" })}
          </p>
          <p className="mt-1 text-2xl font-semibold tabular-nums">
            κ {fmtKappa(agreement?.kappa)}
          </p>
          <p className="text-xs text-muted-foreground">
            {agreement && agreement.items > 0
              ? t("evalStudio.calibration.agreementMeta", {
                  defaultValue: "{{interpretation}} · {{items}} multi-reviewer item(s)",
                  interpretation: agreement.interpretation,
                  items: agreement.items,
                })
              : t("evalStudio.calibration.agreementEmpty", {
                  defaultValue: "No multi-reviewer tasks yet",
                })}
          </p>
        </div>

        <div className="rounded-xl border border-border/60 bg-card/60 p-3">
          <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
            <IconChartHistogram size={13} />
            {t("evalStudio.calibration.model", { defaultValue: "Model vs human" })}
          </p>
          <p className="mt-1 text-2xl font-semibold tabular-nums">
            κ {fmtKappa(calibration?.kappa)}
          </p>
          <p className="text-xs text-muted-foreground">
            {calibration && calibration.audited > 0
              ? t("evalStudio.calibration.modelMeta", {
                  defaultValue: "{{interpretation}} · {{audited}} audited",
                  interpretation: calibration.interpretation,
                  audited: calibration.audited,
                })
              : t("evalStudio.calibration.modelEmpty", { defaultValue: "No audited cases yet" })}
          </p>
        </div>
      </div>

      {calibration && calibration.audited > 0 && (
        <p className="rounded-lg border border-border/60 bg-muted/40 p-3 text-xs text-muted-foreground">
          {calibration.suggestion}
        </p>
      )}

      <div className="flex flex-wrap items-end gap-2 border-t border-border/60 pt-3">
        <div>
          <label
            htmlFor="audit-size"
            className="mb-1 block text-[11px] font-semibold uppercase tracking-wide text-muted-foreground"
          >
            {t("evalStudio.calibration.sampleSize", { defaultValue: "Sample size" })}
          </label>
          <input
            id="audit-size"
            type="number"
            min={1}
            value={sampleSize}
            onChange={(event) => setSampleSize(event.target.value)}
            className="w-24 rounded-lg border border-border/60 bg-card/60 px-3 py-1 text-sm"
          />
        </div>
        <div>
          <label
            htmlFor="audit-reviewers"
            className="mb-1 block text-[11px] font-semibold uppercase tracking-wide text-muted-foreground"
          >
            {t("evalStudio.calibration.reviewers", { defaultValue: "Reviewers / case" })}
          </label>
          <input
            id="audit-reviewers"
            type="number"
            min={1}
            value={reviewers}
            onChange={(event) => setReviewers(event.target.value)}
            className="w-24 rounded-lg border border-border/60 bg-card/60 px-3 py-1 text-sm"
          />
        </div>
        <button
          type="button"
          disabled={sample.isPending}
          onClick={() =>
            sample.mutate({
              sampleSize: Math.max(1, Number(sampleSize) || 1),
              requiredReviewers: Math.max(1, Number(reviewers) || 1),
            })
          }
          className="inline-flex items-center gap-1.5 rounded-lg bg-gradient-to-br from-amber-500 to-rose-500 px-3.5 py-1.5 text-sm font-medium text-white shadow disabled:opacity-50"
        >
          <IconFlask size={16} />
          {t("evalStudio.calibration.sample", { defaultValue: "Audit MODEL-graded run" })}
        </button>
        {sample.isSuccess && (
          <span className="text-xs text-muted-foreground">
            {t("evalStudio.calibration.sampled", {
              defaultValue: "Parked {{count}} audit task(s).",
              count: sample.data?.length ?? 0,
            })}
          </span>
        )}
        {sample.isError && (
          <span className="text-xs text-rose-600 dark:text-rose-400">
            {t("evalStudio.calibration.sampleError", {
              defaultValue: "No MODEL-graded run to audit.",
            })}
          </span>
        )}
      </div>
    </div>
  );
}

function ReviewDetail({
  agentId,
  task,
  onDone,
}: Readonly<{ agentId: string; task: TurEvalReviewTask; onDone: () => void }>) {
  const { t } = useTranslation();
  const { data: datasets } = useEvalDatasets();
  const submit = useSubmitReview(agentId);
  const promote = usePromoteReview(agentId);

  const [pass, setPass] = useState(true);
  const [score, setScore] = useState<string>("");
  const [notes, setNotes] = useState<string>("");
  const [datasetId, setDatasetId] = useState<string>("");
  const [decidedStatus, setDecidedStatus] = useState<string | null>(null);

  const isAudit = task.taskType === "AUDIT";
  const finalized = decidedStatus === "REVIEWED";
  const awaitingMore = decidedStatus !== null && decidedStatus !== "REVIEWED";

  const handleSubmit = () => {
    submit.mutate(
      {
        taskId: task.id,
        request: {
          pass,
          score: score.trim() === "" ? null : Number(score),
          notes: notes.trim() === "" ? null : notes,
        },
      },
      { onSuccess: (updated) => setDecidedStatus(updated.status) },
    );
  };

  return (
    <div className="bento-tile bento-glass space-y-4 rounded-2xl border border-border/60 p-5">
      <div>
        <p className="font-medium">
          {task.caseName ??
            t("evalStudio.review.unnamedCase", { defaultValue: "Unnamed case" })}
        </p>
        <p className="text-xs text-muted-foreground">{task.graderId}</p>
      </div>

      {isAudit && task.modelPass !== null && task.modelPass !== undefined && (
        <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-3 text-xs">
          <span className="font-semibold">
            {t("evalStudio.review.modelVerdict", { defaultValue: "Model verdict" })}:
          </span>{" "}
          {task.modelPass
            ? t("evalStudio.review.pass", { defaultValue: "Pass" })
            : t("evalStudio.review.fail", { defaultValue: "Fail" })}
          {task.modelScore !== null && task.modelScore !== undefined && (
            <> · {task.modelScore.toFixed(2)}</>
          )}
          <p className="mt-1 text-muted-foreground">
            {t("evalStudio.review.auditHint", {
              defaultValue: "Decide whether this MODEL verdict was correct to calibrate the judge.",
            })}
          </p>
        </div>
      )}

      <section>
        <h4 className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          {t("evalStudio.review.transcript", { defaultValue: "Transcript" })}
        </h4>
        <pre className="max-h-64 overflow-auto rounded-lg border border-border/60 bg-muted/40 p-3 text-xs whitespace-pre-wrap">
          {task.transcript ?? ""}
        </pre>
      </section>

      <section>
        <h4 className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
          {t("evalStudio.review.expected", { defaultValue: "Expected" })}
        </h4>
        <p className="rounded-lg border border-border/60 bg-muted/40 p-3 text-xs text-muted-foreground">
          {task.expectedSummary ?? "—"}
        </p>
      </section>

      {finalized && (
        <section className="space-y-3 border-t border-border/60 pt-4">
          <p className="flex items-center gap-2 text-sm text-emerald-600 dark:text-emerald-400">
            <IconChecks size={16} />
            {t("evalStudio.review.submitted", {
              defaultValue: "Verdict submitted and merged into the latest report.",
            })}
          </p>
          {!isAudit && (
            <div className="flex flex-wrap items-end gap-2">
              <div>
                <label
                  htmlFor="promote-dataset"
                  className="mb-1 block text-xs font-semibold uppercase tracking-wide text-muted-foreground"
                >
                  {t("evalStudio.review.promote", { defaultValue: "Promote to golden dataset" })}
                </label>
                <select
                  id="promote-dataset"
                  value={datasetId}
                  onChange={(event) => setDatasetId(event.target.value)}
                  className="min-w-56 rounded-lg border border-border/60 bg-card/60 px-3 py-1.5 text-sm"
                >
                  <option value="">
                    {t("evalStudio.review.pickDataset", { defaultValue: "Select a dataset…" })}
                  </option>
                  {(datasets ?? []).map((dataset) => (
                    <option key={dataset.id} value={dataset.id}>
                      {dataset.name}
                    </option>
                  ))}
                </select>
              </div>
              <button
                type="button"
                disabled={!datasetId || promote.isPending}
                onClick={() =>
                  promote.mutate({ taskId: task.id, datasetId }, { onSuccess: onDone })
                }
                className="inline-flex items-center gap-1.5 rounded-lg bg-gradient-to-br from-emerald-600 to-teal-600 px-3.5 py-1.5 text-sm font-medium text-white shadow disabled:opacity-50"
              >
                <IconTargetArrow size={16} />
                {t("evalStudio.review.promoteAction", { defaultValue: "Promote" })}
              </button>
              {promote.isError && (
                <p className="text-sm text-rose-600 dark:text-rose-400">
                  {t("evalStudio.review.promoteError", { defaultValue: "Could not promote the row." })}
                </p>
              )}
            </div>
          )}
        </section>
      )}

      {awaitingMore && (
        <p className="flex items-center gap-2 border-t border-border/60 pt-4 text-sm text-amber-600 dark:text-amber-400">
          <IconUsers size={16} />
          {t("evalStudio.review.awaitingMore", {
            defaultValue: "Verdict recorded — awaiting more reviewers before consensus.",
          })}
        </p>
      )}

      {!finalized && !awaitingMore && (
        <section className="space-y-3 border-t border-border/60 pt-4">
          {(task.requiredReviewers ?? 1) > 1 && (
            <p className="text-xs text-muted-foreground">
              {t("evalStudio.review.reviewerProgress", {
                defaultValue: "{{have}} of {{need}} reviewers have decided.",
                have: task.verdictCount ?? 0,
                need: task.requiredReviewers,
              })}
            </p>
          )}
          <div className="flex items-center gap-2">
            <span className="text-sm text-muted-foreground">
              {t("evalStudio.review.verdict", { defaultValue: "Verdict" })}
            </span>
            <div className="inline-flex overflow-hidden rounded-lg border border-border/60">
              <button
                type="button"
                onClick={() => setPass(true)}
                className={`px-3 py-1 text-sm ${
                  pass ? "bg-emerald-600 text-white" : "bg-card/60 text-muted-foreground"
                }`}
              >
                {t("evalStudio.review.pass", { defaultValue: "Pass" })}
              </button>
              <button
                type="button"
                onClick={() => setPass(false)}
                className={`px-3 py-1 text-sm ${
                  !pass ? "bg-rose-600 text-white" : "bg-card/60 text-muted-foreground"
                }`}
              >
                {t("evalStudio.review.fail", { defaultValue: "Fail" })}
              </button>
            </div>
            <input
              type="number"
              min={0}
              max={1}
              step={0.1}
              value={score}
              onChange={(event) => setScore(event.target.value)}
              placeholder={t("evalStudio.review.scorePlaceholder", { defaultValue: "score 0–1" })}
              className="w-28 rounded-lg border border-border/60 bg-card/60 px-3 py-1 text-sm"
            />
          </div>
          <textarea
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            rows={3}
            placeholder={t("evalStudio.review.notesPlaceholder", {
              defaultValue: "Notes (optional)…",
            })}
            className="w-full rounded-lg border border-border/60 bg-card/60 px-3 py-2 text-sm"
          />
          <div className="flex items-center gap-3">
            <button
              type="button"
              disabled={submit.isPending}
              onClick={handleSubmit}
              className="inline-flex items-center gap-1.5 rounded-lg bg-gradient-to-br from-blue-600 to-indigo-600 px-3.5 py-1.5 text-sm font-medium text-white shadow disabled:opacity-50"
            >
              <IconSend size={16} />
              {t("evalStudio.review.submit", { defaultValue: "Submit verdict" })}
            </button>
            {submit.isError && (
              <span className="text-sm text-rose-600 dark:text-rose-400">
                {t("evalStudio.review.submitError", { defaultValue: "Could not submit." })}
              </span>
            )}
          </div>
        </section>
      )}
    </div>
  );
}
