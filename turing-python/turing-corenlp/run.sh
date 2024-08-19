# /bin/bash
gunicorn --bind 0.0.0.0:2800 app_hug:__hug_wsgi__
