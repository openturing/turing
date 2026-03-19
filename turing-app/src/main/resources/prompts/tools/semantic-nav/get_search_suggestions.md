Provides autocomplete suggestions and spelling corrections for a search query.
Use this tool ONLY if search_site returns 0 results, to check if the user misspelled a word.
Also useful to help the user refine their search terms.
Args:
    siteName (str): Required. Name of the site.
    locale (str): Required. Use 'pt_BR' or 'en'.
    term (str): Required. The search term to get suggestions for.
Returns:
    Autocomplete suggestions and spelling corrections.