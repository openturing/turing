Retrieves the indexed content of a specific file from the knowledge base.
Use this when you need to read the full content of a particular document.

Use this tool when:
- The user asks to "read", "open", or "show" a specific file's content
- You need detailed content from a known file after listing or searching
- The user references a specific document by name

Args:
    fileName (str): Required. The file name or partial path to search for.
    maxChunks (int): Optional. Maximum chunks to return (1-50). Default: 10.
Returns:
    The text content of the file split into indexed chunks.
