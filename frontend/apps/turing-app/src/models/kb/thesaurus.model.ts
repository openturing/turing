// Block AQ (§XL) — Thesaurus (controlled vocabulary) admin models.
// User-facing name is "Thesaurus"; the backend package/tables are `kb_*`.

export type TurKnowledgeBaseSource =
  | "USER"
  | "SYSTEM_SEED"
  | "AI_GENERATED"
  | "XML_IMPORT";

export type TurKnowledgeBase = {
  id: string;
  name: string;
  description?: string | null;
  source?: TurKnowledgeBaseSource;
};

export type TurMicrothesaurus = {
  id: string;
  name: string;
  description?: string | null;
  language: string; // ISO code, e.g. "pt", "en"
  domain: string; // GENERAL | EDUCATION | ...
  knowledgeBaseId?: string | null;
  termCount?: number;
};

export type TurThesaurusRelationType =
  | "BROADER"
  | "NARROWER"
  | "RELATED"
  | "USE"
  | "USED_FOR"
  | "CUSTOM";

export type TurThesaurusRelationDirectionality =
  | "UNIDIRECTIONAL"
  | "BIDIRECTIONAL";

export type TurThesaurusTermVariation = {
  surfaceForm: string;
  weight?: number;
  caseSensitive?: boolean;
  accentSensitive?: boolean;
  language?: string | null;
};

export type TurThesaurusTermRelation = {
  id?: string | null;
  type: TurThesaurusRelationType;
  directionality?: TurThesaurusRelationDirectionality;
  targetTermId: string;
};

export type TurThesaurusTerm = {
  id: string;
  label: string;
  enabled: boolean;
  termOrder?: number;
  scopeNote?: string | null;
  externalId?: string | null;
  parentTermId?: string | null;
  microthesaurusId?: string | null;
  variations?: TurThesaurusTermVariation[];
  relations?: TurThesaurusTermRelation[];
};

export type TurThesaurusSeed = {
  id: string;
  name: string;
  domain: string;
  language: string;
};

// T675 — LLM-assisted generation (draft, never auto-persisted).
export type TurThesaurusGenerationRequest = {
  domain: string;
  language: string;
  guidance?: string;
  maxTerms?: number;
};

export type TurThesaurusDraftVariation = {
  surfaceForm: string;
  caseSensitive: boolean;
  accentSensitive: boolean;
};

export type TurThesaurusDraftTerm = {
  id: string;
  label: string;
  scopeNote?: string | null;
  broader?: string | null;
  related?: string[];
  variations?: TurThesaurusDraftVariation[];
};

export type TurThesaurusDraft = {
  name: string;
  description?: string | null;
  language: string;
  domain: string;
  terms: TurThesaurusDraftTerm[];
};

// T670 / T681 — per-SN-site selection + index config.
export type TurSNSiteMicrothesaurus = {
  id: string;
  microthesaurusId: string;
  enabled: boolean;
  name?: string | null;
  language?: string | null;
  domain?: string | null;
};

export type TurSNSiteMicrothesaurusConfig = {
  enabled: boolean;
  fieldName: string;
  boost: number;
  includeSynonyms: boolean;
  /** T677 — opt-in hierarchical concept facet (level-prefixed path tokens). */
  pathFacetEnabled: boolean;
  pathFieldName: string;
};
