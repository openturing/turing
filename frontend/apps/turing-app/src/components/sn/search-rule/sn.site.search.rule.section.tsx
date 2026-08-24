/**
 * Search-rule section wrapper — thin re-export of the shared
 * {@link SNFormSection} (T576). Kept as a named alias so the search-rule
 * sub-components (details/conditions/actions) don't need to change imports.
 *
 * T1000 — it used to re-export the `SNFormChrome` type alongside it. That type
 * named a choice nobody made: no call site ever passed `chrome="console"`.
 */
export {
  SNFormSection as SNSiteSearchRuleSection,
} from "@/components/sn/sn-form-section"
