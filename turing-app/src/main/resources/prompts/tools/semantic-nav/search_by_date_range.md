Searches for documents within a specific date range.
Use this INSTEAD of search_site when the user asks for content from a specific period
(e.g., 'news from last year', 'events in October 2023', 'publications between March and June').
This tool handles date syntax automatically - the LLM only needs to provide simple YYYY-MM-DD dates.
Args:
    siteName (str): Required. Site name.
    locale (str): Required. Search locale (e.g., 'pt_BR' or 'en').
    query (str): Required. The search term (or '*' for all content in the date range).
    dateFieldName (str): Required. The name of the date field to filter on
        (must be discovered via get_site_fields, e.g., 'date', 'publication_date', 'modification_date').
    startDate (str): Required. Start date in format YYYY-MM-DD.
    endDate (str): Required. End date in format YYYY-MM-DD. Use the same date as startDate for a single day.
    rows (int): Optional. Number of results. Defaults to 10.