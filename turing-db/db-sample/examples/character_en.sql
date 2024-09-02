SELECT
    id,
    NAME AS title,
    NAME AS cast,
    CONCAT_WS(
        '/',
        'https://cinestamp.com/person',
        id
    ) AS url,
    CONCAT_WS(
        '',
        'https://image.tmdb.org/t/p/w300',
        poster_path
    ) AS image
FROM
    CSMovieDBPerson
LIMIT 20