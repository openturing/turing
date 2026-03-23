Searches server application logs stored in MongoDB.
Use this tool when the user asks about server errors, warnings, application behavior, or wants to investigate server-side issues.

Args:
    level (str): Optional. Log level filter: ERROR, WARN, INFO, DEBUG, TRACE. Leave empty for all levels.
    dateFrom (str): Optional. Start date in ISO format (YYYY-MM-DD). Example: '2026-03-01'.
    dateTo (str): Optional. End date in ISO format (YYYY-MM-DD). Example: '2026-03-21'.
    search (str): Optional. Text to search in message and stackTrace fields (case-insensitive regex).
    rows (int): Optional. Number of results to return (1-50). Default: 20.
    sort (str): Optional. Sort order by date: 'asc' or 'desc'. Default: 'desc' (most recent first).
Returns:
    Log entries with date, level, logger, message, and stack trace (if present).
    Also shows total count matching the criteria.

TIPS:
- To find recent errors: use level='ERROR', sort='desc'
- To investigate a specific exception: use search='NullPointerException' or search='OutOfMemory'
- To analyze a time window: combine dateFrom and dateTo
- Use get_log_stats first to get an overview before diving into specific logs