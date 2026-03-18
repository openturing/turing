Splits a user phrase into individual words and searches ALL facets for values matching each word.
Uses word-stem matching for better recall (e.g., 'docentes' matches 'Docente').
CRITICAL USE CASE: When the user asks to filter by a phrase like 'cursos de engenharia' or
'professores de matemática', call this tool to discover which facet fields and values exist
for EACH word in the phrase. This prevents hallucinating filter values.
IMPORTANT RULE: Words that match a facet value MUST be used in 'filterQueries' and MUST NOT
be repeated in the 'query' parameter of 'search_site'. Only words that did NOT match any facet
should remain in the 'query'. This avoids redundant filtering and improves search precision.
For a single keyword, you may use 'lookup_facet_value' instead.
Args:
    siteName (str): Required. The site name.
    locale (str): Required. The locale (e.g., 'pt_BR' or 'en').
    text (str): Required. A phrase or sentence whose words will be searched individually
        (e.g., 'cursos engenharia computação', 'professores matemática').