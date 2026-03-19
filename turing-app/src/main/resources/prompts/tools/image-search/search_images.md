Searches for images on the web and returns image URLs with descriptions.
Use this tool when the user asks to see, show, or find a photo, picture, or image of something.

Before calling, enrich the user's query with relevant adjectives, context, and scene details.
Always translate the query to English for better results.
Example: user says "foto de gato" -> query: "orange tabby cat sitting on windowsill, natural lighting, sharp focus"

Do not over-optimize — keep the query relevant to what the user actually asked for.

Args:
    query (str): Required. The enriched English query.
    count (int): Optional. Number of images to return (1-8). Default: 3.
Returns:
    A list of image URLs with descriptions. Display as ![description](url).
