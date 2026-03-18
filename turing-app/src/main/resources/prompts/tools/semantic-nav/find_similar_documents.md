Finds documents that are semantically similar to a specific document (More Like This).
Use this when the user asks for recommendations, related articles, or 'more like this'.
Args:
    siteName (str): Required. Name of the site.
    locale (str): Required. Use 'pt_BR' or 'en'.
    documentId (str): Required. The document ID to find similar documents for.