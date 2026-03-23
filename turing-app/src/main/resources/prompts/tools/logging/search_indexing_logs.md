Searches content indexing logs stored in MongoDB.
Use this tool when the user asks about indexing operations, content processing status, failed indexing jobs, or wants to track specific content items.

Args:
    dateFrom (str): Optional. Start date in ISO format (YYYY-MM-DD). Example: '2026-03-01'.
    dateTo (str): Optional. End date in ISO format (YYYY-MM-DD). Example: '2026-03-21'.
    status (str): Optional. Indexing operation status filter (exact match).
    contentId (str): Optional. Content ID to search for (case-insensitive regex match).
    resultStatus (str): Optional. Result status filter: SUCCESS, ERROR, SKIPPED, WARNING (exact match).
    url (str): Optional. URL pattern to search for (case-insensitive regex match).
    rows (int): Optional. Number of results to return (1-50). Default: 20.
    sort (str): Optional. Sort order by date: 'asc' or 'desc'. Default: 'desc' (most recent first).
Returns:
    Indexing log entries with date, status, contentId, resultStatus, URL, title, type, and error message.
    Also shows total count matching the criteria.

TIPS:
- To find failed indexing: use resultStatus='ERROR'
- To track a specific content item: use contentId with the item's ID
- To check indexing for a URL pattern: use url='/path/to/content'
- Use get_log_stats with source='indexing' first for an overview