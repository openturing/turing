Gets a full overview of all indexing records from a connector integration.
Shows all available sources and their indexed content items with status.

Args:
    integrationId (str): Required. The integration instance ID. Call list_integrations first if unknown.
Returns:
    List of available sources and indexing records with objectId, name, status, source, environment,
    locale, transactionId, checksum, created/modified dates, and associated sites.

TIPS:
- Use this for a broad view of what has been indexed
- For targeted lookups, use search_indexing_content instead
- Cross-reference objectId with search_indexing_logs (from logging tools) to trace indexing issues