Searches for content on a site. Returns compact results (ID, title, URL, snippet).
Use get_document_details to read the full content of a specific result.

MANDATORY WORKFLOW — follow these steps IN ORDER:
1. If the user gave ONE word -> call lookup_facet_value. If MULTIPLE words -> call discover_facet_values.
   Check which words match existing facet values.
2. THEN call 'search_site' using the results:
   - Words that MATCHED a facet → put ONLY in 'filterQueries' (with exact value from facet)
   - Words that DID NOT match any facet → put in 'query'
   - If ALL words matched facets → use query='*'
3. Do not put the same word in both 'query' and 'filterQueries'. This causes redundant
   filtering and returns poor results.
4. NEVER invent filter values. ONLY use exact values returned by the facet lookup tools.

Example: User says "graduação tecnologia"
  → discover_facet_values finds: graduação→programas:Graduação, tecnologia→area-de-conhecimento:Tecnologia
  → search_site(query='*', filterQueries='programas:Graduação|area-de-conhecimento:Tecnologia')

Args:
    siteName (str): Required. Call list_sites first if unknown.
    locale (str): Required. Use 'pt_BR' or 'en'.
    query (str): Required. Use '*' when all user terms are covered by filterQueries.
    rows (int): Optional. Number of results. Defaults to 10.
    page (int): Optional. Defaults to 1.
    filterQueries (str): Optional. Format: field_name:VALUE. Separate with pipe (|).
Returns:
    A list of matching documents with ID, title, URL, and snippet.