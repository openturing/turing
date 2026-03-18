Lists all files that have been indexed in the knowledge base, with optional filtering.

Use this tool when the user asks:
- "What files are in the knowledge base?"
- "List my indexed documents"
- "Show me all PDFs in the knowledge base"
- "Find files named 'report' in the knowledge base"

Args:
    filterKeyword (str): Optional. Filter files by name (case-insensitive partial match).
    filterContentType (str): Optional. Filter by content type (e.g., "application/pdf", "text/plain").
Returns:
    A list of indexed files with name, path, type, size, chunk count, and training date.
