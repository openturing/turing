Retrieves the full text and complete metadata of a single document using its ID.
Call this tool when the user asks for specific details about a document found in a
previous search, or if you need to read the full content to answer a complex question.
Args:
    siteName (str): Required. Name of the site.
    locale (str): Required. Use 'pt_BR' or 'en'.
    documentId (str): Required. The document ID from a previous search result.