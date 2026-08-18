/**
 * A field's facet configuration for a given profile (spec 003), as returned by
 * `GET /api/sn/{snSiteId}/field/profile/{profileId}`.
 *
 * `effective` is what the runtime applies (override ∪ global); `overridden` tells,
 * per attribute, whether the value came from the profile override (true) or was
 * inherited from the global field (false); `global` carries the field's global
 * values so the UI can show "inherited: <value>".
 */
export type TurFacetFieldSort = "DEFAULT" | "COUNT" | "ALPHABETICAL";
export type TurFacetFieldKind = "DEFAULT" | "AND" | "OR";
export type TurFacetRange = "DISABLED" | "DAY" | "MONTH" | "YEAR";

/** The overridable facet settings (shared by `effective` and `global`). */
export type TurFacetConfig = {
  facet: number | null;
  facetName: string | null;
  facetSort: TurFacetFieldSort | null;
  facetType: TurFacetFieldKind | null;
  facetItemType: TurFacetFieldKind | null;
  facetRange: TurFacetRange | null;
  secondaryFacet: boolean | null;
  showAllFacetItems: boolean | null;
};

/** Per-attribute: true when the effective value came from the profile override. */
export type TurFacetOverrideFlags = {
  facet: boolean;
  facetName: boolean;
  facetSort: boolean;
  facetType: boolean;
  facetItemType: boolean;
  facetRange: boolean;
  secondaryFacet: boolean;
  showAllFacetItems: boolean;
};

export type TurSNSiteFieldProfileConfig = {
  fieldExtId: string;
  name: string;
  type: string | null;
  enabled: number | null;
  effective: TurFacetConfig;
  overridden: TurFacetOverrideFlags;
  global: TurFacetConfig;
};

/**
 * Override payload: every field is nullable — `null` inherits the global value.
 * All object types (no primitives) so an omitted field is not rejected.
 */
export type TurProfileFieldConfigRequest = TurFacetConfig;
