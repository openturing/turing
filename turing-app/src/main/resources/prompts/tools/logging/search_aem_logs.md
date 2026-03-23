Searches Adobe AEM connector logs stored in MongoDB.
Use this tool when the user asks about AEM integration issues, AEM content synchronization problems, or AEM connector errors.

Args:
    level (str): Optional. Log level filter: ERROR, WARN, INFO, DEBUG, TRACE. Leave empty for all levels.
    dateFrom (str): Optional. Start date in ISO format (YYYY-MM-DD). Example: '2026-03-01'.
    dateTo (str): Optional. End date in ISO format (YYYY-MM-DD). Example: '2026-03-21'.
    search (str): Optional. Text to search in message and stackTrace fields (case-insensitive regex).
    rows (int): Optional. Number of results to return (1-50). Default: 20.
    sort (str): Optional. Sort order by date: 'asc' or 'desc'. Default: 'desc' (most recent first).
Returns:
    AEM log entries with date, level, logger, message, and stack trace (if present).
    Also shows total count matching the criteria.