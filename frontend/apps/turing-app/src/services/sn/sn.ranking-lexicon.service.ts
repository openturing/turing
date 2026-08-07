import axios from "axios";

/**
 * T943 / §LVIII.8 (Block BJ) — what a site's ranking-vocabulary bundle actually
 * did. Ranking is fail-open, so a bundle that does not parse is ignored at request
 * time and the admin's only other evidence is that questions still rank the way
 * they did before.
 */
export interface TurRankingLexiconPreview {
  /** False when the bundle could not be read — {@link error} says why. */
  valid: boolean;
  error?: string | null;
  /** The language whose bundle this one extends (`pt_BR` resolves to `pt`). */
  language: string;
  /** Words the bundle contributed; a word the shipped vocabulary already had is not one. */
  addedAscWords: string[];
  addedDescWords: string[];
  addedSortTriggers: string[];
  /** Each declared bridge, rendered as `word → field tokens`. */
  addedSynonyms: string[];
  /** True when the sample question resolved a sort under this bundle. */
  sortResolved: boolean;
  field?: string | null;
  order?: string | null;
  /** What the faceting pass would see once the ranking clause is stripped. */
  strippedQuery?: string | null;
}

export interface TurRankingLexiconPreviewRequest {
  siteName: string;
  locale?: string;
  lexicon?: string;
  query?: string;
}

/** Client for the ranking-vocabulary check (`/api/sn/ranking-lexicon`). */
export class TurSNRankingLexiconService {
  /**
   * Report what `lexicon` adds and — when `query` is given — the sort the site
   * would get. The bundle travels in the request, so a draft can be checked
   * before it is saved.
   */
  async preview(
    request: TurRankingLexiconPreviewRequest,
  ): Promise<TurRankingLexiconPreview> {
    const response = await axios.post<TurRankingLexiconPreview>(
      "/sn/ranking-lexicon/preview",
      request,
    );
    return response.data;
  }
}
