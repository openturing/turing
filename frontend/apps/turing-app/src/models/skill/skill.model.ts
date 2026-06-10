/**
 * T318 — client models for the Anthropic-compatible skill folders surfaced by
 * `/api/skill`. A skill is a folder in object storage; the editor works over
 * paths relative to that folder's root.
 */

/** A row of the thin skill catalog index (what the `/admin/skill` list shows). */
export interface TurSkillSummary {
  id: string;
  name: string;
  version: string | null;
  author: string | null;
  description: string | null;
  path: string;
  enabled: boolean;
}

/** One entry of a skill folder's file tree — path is relative to the skill root. */
export interface TurSkillFileNode {
  path: string;
  name: string;
  directory: boolean;
  size: number;
}

/** A file's relative path plus its UTF-8 text contents. */
export interface TurSkillFileContent {
  path: string;
  content: string;
}
