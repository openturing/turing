Fetches a web page by URL and returns its text content.
Use this tool when the user asks about a URL, wants to read an article, or needs information from a specific web page.
Args:
    url (str): The full URL to fetch (e.g., 'https://example.com/page'). Required.
    includeLinks (str): Set to 'yes' to also include a list of links found on the page. Default is 'no'.
Returns:
    The page title and main text content, optionally followed by links found on the page.
