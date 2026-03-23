Gets summary statistics for a log source, including total count and breakdown by level or status.
Use this tool FIRST when the user asks about logs to get an overview before diving into specific entries.

Args:
    source (str): Required. Log source: 'server', 'indexing', or 'aem'.
    dateFrom (str): Optional. Start date in ISO format (YYYY-MM-DD). Example: '2026-03-01'.
    dateTo (str): Optional. End date in ISO format (YYYY-MM-DD). Example: '2026-03-21'.
Returns:
    Total entry count and breakdown:
    - For server/aem: count per log level (ERROR, WARN, INFO, DEBUG, TRACE)
    - For indexing: count per result status (SUCCESS, ERROR, SKIPPED, WARNING)

TIPS:
- Always call this first to understand the volume and distribution of logs
- Then use search_server_logs, search_indexing_logs, or search_aem_logs to drill into specifics
- Combine with date range to analyze trends over time