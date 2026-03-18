Looks up a SINGLE term across all available facets to find exact filter values in the database.
Uses word-stem matching so inflected forms (plurals, conjugations) also match.
CRITICAL: Call this BEFORE using 'filterQueries' in 'search_site' when you know a single keyword
but not the exact database value (e.g., 'docente', 'inovacao', 'silva').
IMPORTANT RULE: If the term matches a facet value, use it in 'filterQueries' and DO NOT repeat
the same term in the 'query' parameter of 'search_site'. Only use 'query' for words that did
not match any facet. This avoids redundant filtering and improves search precision.
For multi-word user phrases, prefer 'discover_facet_values' instead.
Args:
    siteName (str): Required. The site name.
    locale (str): Required. The locale (e.g., 'pt_BR' or 'en').
    term (str): Required. A single word or short term to look for inside facet values.
Returns:
    Matching facet values across all fields for the given term.