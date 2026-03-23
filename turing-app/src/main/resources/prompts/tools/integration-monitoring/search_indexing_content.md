Searches and filters indexing records with advanced criteria. Supports pagination.
This is the most powerful tool for investigating specific content items and their indexing status.

IMPORTANT: Use this tool to cross-reference content with logging tools:
- Find a content item here by objectId, then search_indexing_logs with the same contentId
- Compare status here vs resultStatus in logs to detect inconsistencies

Args:
    integrationId (str): Required. The integration instance ID. Call list_integrations first if unknown.
    objectId (str): Optional. Content object ID to search for (exact match). KEY for cross-referencing.
    source (str): Optional. Filter by source name.
    status (str): Optional. Filter by indexing status (e.g., INDEXED, PENDING, IGNORED).
    environment (str): Optional. Filter by environment.
    locale (str): Optional. Filter by locale (e.g., 'pt_BR', 'en').
    site (str): Optional. Filter by site name.
    dateFrom (str): Optional. Start date in ISO format (YYYY-MM-DD).
    dateTo (str): Optional. End date in ISO format (YYYY-MM-DD).
    rows (int): Optional. Number of results (1-50). Default: 20.
    sortDirection (str): Optional. 'asc' or 'desc'. Default: 'desc'.
Returns:
    Paginated results with total count, available facets (sources, environments, locales, sites),
    and indexing records with full details.

CROSS-REFERENCING WORKFLOW:
1. search_indexing_content(objectId='ABC123') → find the content status in the connector
2. search_indexing_logs(contentId='ABC123') → find the indexing log entries
3. Compare: if connector says INDEXED but logs show ERROR → inconsistency detected
4. search_server_logs(search='ABC123') → check for server-side errors related to this content