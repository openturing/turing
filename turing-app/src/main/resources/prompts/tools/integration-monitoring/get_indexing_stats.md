Gets indexing operation statistics showing throughput and timing for bulk operations.
Shows historical indexing runs with document count and processing speed.

Args:
    integrationId (str): Required. The integration instance ID. Call list_integrations first if unknown.
    source (str): Optional. Filter stats by source name. If empty, returns all sources.
Returns:
    List of indexing operations with: provider, source, operation type (INDEX_ALL/REINDEX_ALL),
    start/end time, document count, and documents per minute throughput.

TIPS:
- Use to understand indexing performance and identify slow runs
- Compare documentsPerMinute across runs to spot degradation
- Check if latest run completed (has endTime) or is still in progress