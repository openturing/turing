import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  IconChecks,
  IconInbox,
  IconRobot,
  IconSend,
  IconTargetArrow,
} from "@tabler/icons-react";

import { useAiAgents } from "@/api/queries/ai-agent.queries";
import {
  useEvalDatasets,
  useEvalReviewInbox,
  usePromoteReview,
  useSubmitReview,
} from "@/api/queries/eval-studio.queries";
import type { TurEvalReviewTask } from "@/models/eval/eval-studio.model";

/**
 * T593 / §XXXIII.8 — the human-review inbox, folded into the Eval Studio. Pick
 * an agent, read the transcript + expected summary of a parked HUMAN-grader
 * task, submit a {pass, score, notes} verdict (which merges back into the
 * latest report), and — closing the loop — promote the row into a golden
 * dataset in one click.
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
                "Tasks appear here when an agent's human-review grader defers a case on a real eval run.",
            })}
          </p>
        </div>
      )}

      {agentId && (tasks?.length ?? 0) > 0 && (
        <div className="grid grid-cols-1 gap-4 lg:grid-cols-[280px_1fr]">
          <ul className="space-y-2">
            {(tasks ?? []).map((task) => {
              const isActive = task.id === selectedTaskId;
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
                    <p className="mt-0.5 truncate text-xs text-muted-foreground">
                      {task.graderId}
                    </p>
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
  const [reviewed, setReviewed] = useState(false);

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
      { onSuccess: () => setReviewed(true) },
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

      {reviewed ? (
        <section className="space-y-3 border-t border-border/60 pt-4">
          <p className="flex items-center gap-2 text-sm text-emerald-600 dark:text-emerald-400">
            <IconChecks size={16} />
            {t("evalStudio.review.submitted", {
              defaultValue: "Verdict submitted and merged into the latest report.",
            })}
          </p>
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
                promote.mutate(
                  { taskId: task.id, datasetId },
                  { onSuccess: onDone },
                )
              }
              className="inline-flex items-center gap-1.5 rounded-lg bg-gradient-to-br from-emerald-600 to-teal-600 px-3.5 py-1.5 text-sm font-medium text-white shadow disabled:opacity-50"
            >
              <IconTargetArrow size={16} />
              {t("evalStudio.review.promoteAction", { defaultValue: "Promote" })}
            </button>
          </div>
          {promote.isError && (
            <p className="text-sm text-rose-600 dark:text-rose-400">
              {t("evalStudio.review.promoteError", { defaultValue: "Could not promote the row." })}
            </p>
          )}
        </section>
      ) : (
        <section className="space-y-3 border-t border-border/60 pt-4">
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
