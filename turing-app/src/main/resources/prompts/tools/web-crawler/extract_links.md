Extracts all links from a web page, optionally filtered by a keyword.
Use this tool to discover navigation, find sub-pages, or locate specific resources on a site.
Args:
    url (str): The full URL to crawl (e.g., 'https://example.com'). Required.
    filterKeyword (str): Optional keyword to filter links by text or URL. Use empty string to return all links.
Returns:
    A list of links (text and URL) found on the page.
