Retrieves the newest or most recently updated content about a topic.
Use this IMMEDIATELY when the user asks for 'latest', 'newest', 'recent news',
'updates', or 'what happened recently'. This tool handles date sorting automatically,
so the LLM does NOT need to build date filter queries.
Args:
    siteName (str): Required. Name of the site.
    locale (str): Required. Use 'pt_BR' or 'en'.
    query (str): Required. The topic to search for. Use '*' for all recent content.
    rows (int): Optional. Number of recent results. Defaults to 5.
    filterQueries (str): Optional. Additional filters. Format: field_name:VALUE. Separate with pipe (|).