Extracts specific sentences or paragraphs from a document where the search terms appear.
Use this for precise questions about long documents (rules, regulations, chapters) to save context.
Instead of reading the full document, this returns only the relevant highlighted passages.
Requires highlighting to be enabled on the site.
Args:
    siteName (str): Required. Name of the site.
    locale (str): Required. Use 'pt_BR' or 'en'.
    documentId (str): Required. The document ID to search within.
    termsToHighlight (str): Required. The search terms to find within the document.