Provides a statistical summary of ALL categories and attributes available for a given search query.
Call this tool when the user asks 'What types of X do you have?', 'How is the content organized?',
'What categories exist?', or wants a panoramic overview of available content without reading documents.
Unlike get_valid_filter_values (which returns values for ONE field), this returns ALL facet fields at once.
Args:
    siteName (str): Required. Site name.
    locale (str): Required. Search locale (e.g., 'pt_BR' or 'en').
    query (str): Optional. The search term to scope the summary. Use '*' for the entire site.
Returns:
    A summary of all facet fields and their value counts for the query.