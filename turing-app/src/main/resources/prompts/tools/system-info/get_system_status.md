Gets a comprehensive overview of the Viglet Turing ES system status.
Shows application version, database, JVM memory, disk space, MongoDB, MinIO, and JVM details.
Use this tool FIRST when investigating system health or performance issues.

Args: none
Returns:
    System status report including:
    - App version
    - Database status and product info
    - JVM heap memory usage (used/max with percentage)
    - Disk space usage (used/total with percentage)
    - MongoDB status (UP/DOWN/DISABLED)
    - MinIO object storage status (UP/DOWN/DISABLED)
    - JVM version, OS info, and processor count

TIPS:
- Call this first to get a quick health overview
- If memory usage is high (>80%), use get_memory_details for deeper analysis
- Check database status before investigating indexing or search issues
- Cross-reference with search_server_logs to correlate errors with resource usage