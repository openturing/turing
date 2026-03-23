Lists all configured integration connector instances.
Use this tool FIRST to discover available integrations before calling other integration monitoring tools.
Each integration represents a connector microservice (e.g., AEM connector, Web Crawler).

Args: none
Returns:
    A table with: id, title, vendor, endpoint, enabled status.
    Use the 'id' value as the 'integrationId' parameter in other integration tools.

TIPS:
- Always call this first to get valid integration IDs
- Disabled integrations will not respond to monitoring queries