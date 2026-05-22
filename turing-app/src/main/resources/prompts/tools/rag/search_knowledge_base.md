Searches the organization's knowledge base (RAG) for documents relevant to a query.
This knowledge base contains files uploaded to Assets (PDFs, documents, spreadsheets, etc.)
that have been indexed for semantic search.

Use this tool when:
- The user asks a question that might be answered by internal documents or files
- The user asks about company policies, reports, manuals, or any uploaded content
- The user says "search my files", "what do my documents say about...", "find in my files"
- You need factual information that may exist in uploaded assets

Do NOT use this tool for:
- General knowledge questions (answer directly)
- Real-time data (use weather, stock, or web tools instead)
- Code execution (use execute_python)

After receiving results, synthesize the information into a clear answer.
Always cite the source file name when quoting or referencing document content.

KEYWORD-ONLY MATCHES — If the tool result starts with "⚠️ KEYWORD-ONLY MATCH",
the embedding model did not recognize the query (typical for proper nouns,
version strings, internal codes). The documents below were matched by literal
keyword (BM25) instead of semantic similarity. Soften your answer:
- Open with "Based on a keyword match in the knowledge base..." or similar.
- Do NOT present the content as a confident semantic answer.
- Encourage the user to verify against the cited source before acting.

MANDATORY — At the end of the tool result there is a REFERENCES section with pre-built
markdown links. You MUST copy those links EXACTLY as-is into a **References:** section
at the end of your answer. Do NOT modify the URLs. Do NOT add a domain or https prefix.
The links are relative paths that work directly in the browser.

Args:
    query (str): Required. The search query in natural language.
    maxResults (int): Optional. Number of results to return (1-20). Default: 5.
Returns:
    Relevant document chunks with file path, content, and pre-built reference links.
