FROM grafana/grafana:11.4.0

# Provisioned datasource (Prometheus) and the Turing search dashboard.
COPY provisioning/ /etc/grafana/provisioning/
COPY dashboards/   /var/lib/grafana/dashboards/

# Anonymous, read-only access on the home dashboard so devs don't bother with
# the admin/admin login during local exploration. Override these in production.
#
# yesoreyeram-infinity-datasource lets dashboards read JSON from the
# Turing backend (POST /api/system/chat-analytics/*) without coupling
# Grafana to the underlying chat analytics engine (Mongo or Redis).
ENV GF_AUTH_ANONYMOUS_ENABLED=true \
    GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer \
    GF_USERS_ALLOW_SIGN_UP=false \
    GF_INSTALL_PLUGINS=yesoreyeram-infinity-datasource
