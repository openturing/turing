/**
 * Synthetic User Research — API models + presentation helpers (Block AW / §XLVI,
 * T730). Types mirror the backend DTOs (`TurResearchStudyDto`,
 * `TurResearchReportDto`, `TurResearchSaturationResultDto`, `TurResearchGraphDto`,
 * `TurResearchDriftDto`, `TurResearchConceptFitDto`, `TurResearchRunEvent`) so the
 * studio renders straight off the wire. The Phase 2–4 backends are deliberately
 * headless; this is the single rendering surface for all of them.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

export type ResearchProtocol = "DYNAMIC_SCRIPT" | "CUSTOM_SCRIPT" | "CONCEPT_TEST";
export type ResearchSchedule = "MANUAL" | "DAILY" | "WEEKLY";
export type ResearchInterviewStatus = "PENDING" | "RUNNING" | "COMPLETED" | "FAILED";
export type ResearchPersonaKind = "SPEAKER" | "AUDIENCE" | "BOTH";

export const RESEARCH_PROTOCOLS: ResearchProtocol[] = [
  "DYNAMIC_SCRIPT",
  "CUSTOM_SCRIPT",
  "CONCEPT_TEST",
];
export const RESEARCH_SCHEDULES: ResearchSchedule[] = ["MANUAL", "DAILY", "WEEKLY"];

/** A resolved reference to a persona in the study's ordered audience roster. */
export interface ResearchPersonaRef {
  id: string;
  name: string;
  kind: ResearchPersonaKind;
  position: number;
}

/** One question/answer turn in an interview transcript. */
export interface ResearchTurn {
  index: number;
  question: string;
  answer: string;
}

/** One persona's interview transcript within a study. */
export interface ResearchInterview {
  id: string;
  personaId: string;
  personaName: string;
  status: ResearchInterviewStatus;
  turnCount: number;
  error?: string | null;
  startedAt?: string | null;
  completedAt?: string | null;
  turns: ResearchTurn[];
}

/** Study summary (list) — roster/interviews omitted, counts present. */
export interface ResearchStudySummary {
  id: string;
  name: string;
  goal?: string | null;
  hypothesis?: string | null;
  description?: string | null;
  enabled: boolean;
  protocol: ResearchProtocol;
  conceptText?: string | null;
  maxQuestions: number;
  llmInstanceId?: string | null;
  llmName?: string | null;
  targetAgentId?: string | null;
  targetAgentName?: string | null;
  interviewLlmInstanceId?: string | null;
  synthesisLlmInstanceId?: string | null;
  schedule: ResearchSchedule;
  lastRunAt?: string | null;
  personaCount: number;
  interviewCount: number;
}

/** Study detail — roster + interviews populated. */
export interface ResearchStudy extends ResearchStudySummary {
  questions: string[];
  personas: ResearchPersonaRef[];
  interviews: ResearchInterview[];
}

/** Create/update request body (subset the backend reads on write). */
export interface ResearchStudyRequest {
  name: string;
  goal?: string | null;
  hypothesis?: string | null;
  description?: string | null;
  enabled: boolean;
  protocol: ResearchProtocol;
  conceptText?: string | null;
  questions?: string[];
  maxQuestions?: number;
  llmInstanceId?: string | null;
  targetAgentId?: string | null;
  interviewLlmInstanceId?: string | null;
  synthesisLlmInstanceId?: string | null;
  schedule: ResearchSchedule;
}

// ---------------------------------------------------------------------------
// Synthesis & sufficiency (Phase 3).
// ---------------------------------------------------------------------------

export interface ResearchQuote {
  personaId: string;
  personaName: string;
  quote: string;
  resolved: boolean;
}

export interface ResearchTheme {
  title: string;
  summary: string;
  prevalence: number;
  quotes: ResearchQuote[];
}

export interface ResearchPersonaLensQuote {
  theme: string;
  quote: string;
}

export interface ResearchPersonaLens {
  personaId: string;
  personaName: string;
  quotes: ResearchPersonaLensQuote[];
}

export interface ResearchReport {
  available: boolean;
  error?: string | null;
  canRegenerate: boolean;
  executiveSummary?: string | null;
  themes: ResearchTheme[];
  recommendations: string[];
  byPersona: ResearchPersonaLens[];
  rawContent?: string | null;
}

export interface ResearchSaturationStep {
  index: number;
  personaId: string;
  personaName: string;
  personaThemes: number;
  newThemes: number;
  cumulativeThemes: number;
  noveltyRatio: number;
}

export interface ResearchSaturation {
  available: boolean;
  message?: string | null;
  personaCount: number;
  totalUniqueThemes: number;
  saturated: boolean;
  adequateAtN: number;
  noveltyThreshold: number;
  minDryStreak: number;
  steps: ResearchSaturationStep[];
}

export interface ResearchGraphNode {
  id: string;
  label: string;
  type: "THEME" | "PERSONA";
  weight: number;
}

export interface ResearchGraphEdge {
  source: string;
  target: string;
  weight: number;
}

export interface ResearchGraph {
  available: boolean;
  message?: string | null;
  nodes: ResearchGraphNode[];
  edges: ResearchGraphEdge[];
}

// ---------------------------------------------------------------------------
// Interconnection (Phase 4).
// ---------------------------------------------------------------------------

export interface ResearchDriftPoint {
  capturedAt: string;
  interviewCount: number;
  totalUniqueThemes: number;
  adequateAtN: number;
  saturated: boolean;
  newThemesSincePrevious: number;
}

export interface ResearchDrift {
  schedule: ResearchSchedule;
  snapshotCount: number;
  points: ResearchDriftPoint[];
}

export interface ContentFitMisfit {
  span: string;
  reason: string;
  suggestion: string;
}

export interface ContentFitResult {
  fitScore: number;
  summary?: string | null;
  fits: string[];
  misfits: ContentFitMisfit[];
  readabilityScore: number;
  llmUsed: boolean;
  error?: string | null;
}

export interface ResearchConceptFitEntry {
  personaId: string;
  personaName: string;
  result: ContentFitResult;
}

export interface ResearchConceptFit {
  available: boolean;
  error?: string | null;
  conceptText?: string | null;
  averageFitScore: number;
  personas: ResearchConceptFitEntry[];
}

// ---------------------------------------------------------------------------
// Multi-study program rollup — PRISMA (Phase 5, T733).
// ---------------------------------------------------------------------------

export interface ResearchProgramStudy {
  studyId: string;
  name: string;
  protocol: ResearchProtocol;
  participants: number;
  interviews: number;
  reportAvailable: boolean;
  themeCount: number;
  saturated: boolean;
  adequateAtN: number;
  lastRunAt?: string | null;
}

export interface ResearchProgramTheme {
  title: string;
  studyCount: number;
  totalPrevalence: number;
  studyNames: string[];
}

export interface ResearchProgramRollup {
  available: boolean;
  message?: string | null;
  studyCount: number;
  totalParticipants: number;
  totalInterviews: number;
  totalDistinctThemes: number;
  sharedThemeCount: number;
  studies: ResearchProgramStudy[];
  themes: ResearchProgramTheme[];
}

// ---------------------------------------------------------------------------
// Research Assistant — guided authoring (Phase 5, T732).
// ---------------------------------------------------------------------------

/** A proposed study configuration from the Research Assistant (never auto-applied). */
export interface ResearchStudyProposal {
  name: string;
  goal: string;
  hypothesis: string;
  protocol: ResearchProtocol;
  conceptText: string;
  questions: string[];
  maxQuestions: number;
  suggestedCohortBrief: string;
  suggestedParticipantCount: number;
  assistantMessage: string;
}

export interface ResearchProposalResult {
  success: boolean;
  error?: string | null;
  proposal?: ResearchStudyProposal | null;
}

/** SSE progress event streamed by the cohort interview runner. */
export interface ResearchRunEvent {
  type: "STARTED" | "INTERVIEW" | "DONE" | "ERROR";
  studyId: string;
  total: number;
  completed: number;
  interview?: ResearchInterview | null;
  lastRunAt?: string | null;
  error?: string | null;
}

// ---------------------------------------------------------------------------
// Presentation helpers (pure).
// ---------------------------------------------------------------------------

/** Semantic fit level — translated at the render site (i18n). */
export type FitLevel = "high" | "medium" | "low";

export interface FitTone {
  fill: string;
  soft: string;
  text: string;
  border: string;
  level: FitLevel;
}

/** Shared fit colour scale, matching the Persona Match heatmap. */
export function fitTone(score: number): FitTone {
  if (score >= 75) {
    return {
      fill: "bg-emerald-500",
      soft: "bg-emerald-500/10",
      text: "text-emerald-700 dark:text-emerald-300",
      border: "border-emerald-500/30",
      level: "high",
    };
  }
  if (score >= 50) {
    return {
      fill: "bg-amber-500",
      soft: "bg-amber-500/10",
      text: "text-amber-700 dark:text-amber-300",
      border: "border-amber-500/30",
      level: "medium",
    };
  }
  return {
    fill: "bg-rose-500",
    soft: "bg-rose-500/10",
    text: "text-rose-700 dark:text-rose-300",
    border: "border-rose-500/30",
    level: "low",
  };
}

/** Tailwind classes for an interview-status badge. */
export function interviewStatusTone(status: ResearchInterviewStatus): string {
  switch (status) {
    case "COMPLETED":
      return "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300";
    case "FAILED":
      return "border-rose-500/30 bg-rose-500/10 text-rose-700 dark:text-rose-300";
    case "RUNNING":
      return "border-blue-500/30 bg-blue-500/10 text-blue-700 dark:text-blue-300";
    default:
      return "border-border/60 bg-card/40 text-muted-foreground";
  }
}
