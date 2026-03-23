Gets detailed database connection status and metadata.
Use this when investigating database-related issues or checking connectivity.

Args: none
Returns:
    Database details: status (UP/DOWN), product name, version, driver, JDBC URL,
    user, max connections, read-only mode, and auto-commit setting.

TIPS:
- Check status first before investigating data-related issues
- Max connections limit may cause connection pool exhaustion under load
- The JDBC URL reveals which database type (H2, MySQL, PostgreSQL) is in use