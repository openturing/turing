pip install -U pip setuptools wheel
pip install -U spacy gunicorn waitress hug_middleware_cors hug
python -m spacy download en_core_web_sm
python -m spacy download pt_core_news_sm
waitress-serve --listen=0.0.0.0:2800 app_hug:__hug_wsgi__
