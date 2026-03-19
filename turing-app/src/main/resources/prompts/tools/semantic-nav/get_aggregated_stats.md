Calculates totals and distributions for a specific category using facet aggregation.
Call this when the user asks 'how many', 'what is the distribution', 'count by category',
or wants statistical data about content WITHOUT needing to read the actual documents.
Much more efficient than reading all results and counting manually.
Args:
    siteName (str): Required. Name of the site.
    locale (str): Required. Use 'pt_BR' or 'en'.
    query (str): Required. The search query to scope the aggregation (e.g., 'Pos-Graduacao', '*' for all).
    facetField (str): Required. The field to aggregate by (e.g., 'area_conhecimento', 'type', 'author').
    filterQueries (str): Optional. Additional filters. Format: field_name:VALUE. Separate with pipe (|).
Returns:
    Counts and distributions grouped by the specified facet field.