Validates consistency between the connector database and the search engine index.
Detects content that exists in the database but is missing from the index (or vice versa).
This is a double-check tool for finding indexing gaps.

Args:
    integrationId (str): Required. The integration instance ID. Call list_integrations first if unknown.
    connectorName (str): Required. The connector/source name to validate.
Returns:
    - Missing: items in the connector database that are NOT in the search index (should be indexed)
    - Extra: items in the search index that are NOT in the connector database (orphaned entries)
    - If both are empty: the index is consistent

TIPS:
- Use this when users report missing search results
- Cross-reference missing IDs with search_indexing_content and search_indexing_logs
- Extra items may indicate deleted content that wasn't de-indexed