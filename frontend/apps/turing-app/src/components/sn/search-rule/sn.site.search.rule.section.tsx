/**
 * Search-rule section wrapper — thin re-export of the shared, chrome-aware
 * {@link SNFormSection} (T576). Kept as a named alias so the search-rule
 * sub-components (details/conditions/actions) don't need to change imports.
 */
export {
    SNFormSection as SNSiteSearchRuleSection,
    type SNFormChrome as SNSectionChrome,
} from "@/components/sn/sn-form-section"
