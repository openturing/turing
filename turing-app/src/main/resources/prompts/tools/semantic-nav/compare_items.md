Compares specific fields of two or more documents side-by-side in a structured table format.
Call this when the user asks for differences, similarities, or a comparison between courses,
articles, events, or any other content items.
Use get_site_fields to discover available field names before calling this tool.
Args:
    siteName (str): Required. Name of the site.
    locale (str): Required. Use 'pt_BR' or 'en'.
    documentIds (str): Required. Comma-separated document IDs (2 to 5 IDs).
    fieldsToCompare (str): Required. Comma-separated field names to compare
        (e.g., 'title,description,author,date,url').
Returns:
    A side-by-side comparison table of the specified fields.