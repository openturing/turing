# /bin/bash
gunicorn --bind 0.0.0.0:2810 turing-polyglot:__hug_wsgi__
