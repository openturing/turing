Searches for images on the web and returns image URLs with descriptions.
Use this tool when the user asks to see, show, or find a photo, picture, or image of something.
Examples: 'show me a photo of the Eiffel Tower', 'find an image of a Nintendo NES',
'what does a capybara look like?', 'picture of the Mars rover'.

IMPORTANT — Before calling this tool, you MUST act as a Prompt Engineer specialized in computer vision and transform the user's simple request into an optimized super-query:
1. Subject: Identify the main object and add texture, color, and state adjectives (e.g., from 'car' to 'polished vintage red sports car').
2. Context/Scene: Place the object in a specific environment (e.g., 'on an Italian coastal road during golden hour sunset').
3. Technical parameters: Append professional photography terms such as 'shallow depth of field', '8k', 'volumetric lighting', 'sharp focus', 'cinematic style'.
4. Language: The query MUST always be in English, as image search engines and AI models have higher accuracy in English.
5. Output: Pass ONLY the optimized English string as the query — no explanations or extra text.

Args:
    query (str): Required. The optimized super-query in English (e.g., 'polished vintage red sports car on Italian coastal road, golden hour sunset, shallow depth of field, 8k, volumetric lighting, cinematic style').
    count (int): Optional. Number of images to return (1-8). Defaults to 3.
