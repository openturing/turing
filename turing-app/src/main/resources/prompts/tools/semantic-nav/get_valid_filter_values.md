Returns the exact, valid string values that can be used for a specific filter (facet) in search_site.
Use this tool BEFORE applying a filter in search_site if you are not 100% sure of the exact value name
in the database. This prevents hallucinating filter values.
Args:
    siteName (str): Required. Name of the site.
    locale (str): Required. Use 'pt_BR' or 'en'.
    fieldName (str): Required. The facet field name (e.g., 'temas', 'author', 'type').