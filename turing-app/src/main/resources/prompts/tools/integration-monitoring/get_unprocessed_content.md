Gets content items that have not been processed (indexed) yet for a given source.
These are items pending in the connector queue that haven't been sent to the search engine.

Args:
    integrationId (str): Required. The integration instance ID. Call list_integrations first if unknown.
    source (str): Required. The source name to check for unprocessed content.
Returns:
    List of unprocessed indexing records with objectId, name, status, and details.

TIPS:
- Use this to check if there's a backlog of content waiting to be indexed
- If the list is large, the connector may be stalled or overloaded
- Cross-reference with get_indexing_stats to see if an indexing operation is currently running
- Check search_server_logs for errors that may explain why processing stopped