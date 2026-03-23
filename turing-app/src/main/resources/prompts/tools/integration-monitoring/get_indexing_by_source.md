Gets indexing records filtered by a specific source from a connector integration.
A source typically represents a content repository or site being indexed.

Args:
    integrationId (str): Required. The integration instance ID. Call list_integrations first if unknown.
    source (str): Required. The source name to filter by (e.g., site name or content repository).
Returns:
    Indexing records for the specified source with objectId, name, status, environment,
    locale, transactionId, checksum, dates, and associated sites.

TIPS:
- Use get_indexing_overview first to see available source names
- Useful for isolating indexing issues to a specific content source