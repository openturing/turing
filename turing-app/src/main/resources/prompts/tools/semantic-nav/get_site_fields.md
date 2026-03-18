Retrieves valid facet fields for filtering a specific site.
ONLY call this if the user asks for specific filters (e.g., 'by author', 'only news')
and you need to know the exact field name to use in search_site.
Args:
    siteName (str): Name of the site.