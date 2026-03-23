Gets the status of external services: MongoDB and MinIO object storage.
Use this to verify external dependencies are running and accessible.

Args: none
Returns:
    Status for each service:
    - MongoDB: enabled/disabled, UP/DOWN, version, URI
    - MinIO: enabled/disabled, UP/DOWN, endpoint, server info

TIPS:
- MongoDB is required for logging — if DOWN, logging tools will not work
- MinIO is required for file attachments and knowledge base — if DOWN, RAG tools will fail
- Check these statuses when other tools return connection errors