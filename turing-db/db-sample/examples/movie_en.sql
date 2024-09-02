SELECT
    m3.*,
    GROUP_CONCAT(c.name) AS tv
FROM
    (
    SELECT
        m2.*,
        GROUP_CONCAT(s.provider) AS streaming
    FROM
        (
        SELECT
            m1.*,
            GROUP_CONCAT(g.name) AS genres
        FROM
            (
            SELECT
                m.csmoviedb_id AS id,
                m.original_title AS title,
                m.overview AS text,
                m.release_date AS publication_date,
                CONCAT_WS(
                    '',
                    'https://image.tmdb.org/t/p/w300',
                    m.poster_path
                ) AS image,
                CONCAT_WS(
                    '/',
                    'https://cinestamp.com/movie',
                    m.csmoviedb_id
                ) AS url,
                GROUP_CONCAT(p.name) AS cast,
                GROUP_CONCAT(c.persona) AS persona
            FROM
                CSMovieDBLanguage m
            LEFT JOIN CSMovieDBCast c ON
                (
                    m.csmoviedb_id = c.moviedb_id AND m.language = 'en'
                )
            LEFT JOIN CSMovieDBPerson p ON
                (c.moviedb_person_id = p.id)
            GROUP BY
                m.csmoviedb_id
            LIMIT 20
        ) m1
    LEFT JOIN CSMovieDBExtra e ON
        (m1.id = e.csmoviedb_id)
    LEFT JOIN CSMovieDBGenresLanguage g ON
        (
            e.extra_type_id = g.moviedb_genres_id AND e.extra_type = 'genres' AND g.language = 'en'
        )
    GROUP BY
        m1.id
    ) m2
LEFT JOIN CSStreaming s ON
    (
        m2.id = s.csmoviedb_id AND s.language = 'pt'
    )
GROUP BY
    m2.id
) m3
LEFT JOIN CSTVGuide t ON
    (m3.id = t.csmoviedb_id)
LEFT JOIN CSChannel c ON
    (t.cschannel_id = c.id AND t.jp_area_country_id = 1)
GROUP BY
    m3.id