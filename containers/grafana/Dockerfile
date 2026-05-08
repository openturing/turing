FROM grafana/grafana:11.4.0

# Provisioned datasource (Prometheus) and the Turing search dashboard.
COPY provisioning/ /etc/grafana/provisioning/
COPY dashboards/   /var/lib/grafana/dashboards/

# Anonymous, read-only access on the home dashboard so devs don't bother with
# the admin/admin login during local exploration. Override these in production.
ENV GF_AUTH_ANONYMOUS_ENABLED=true \
    GF_AUTH_ANONYMOUS_ORG_ROLE=Viewer \
    GF_USERS_ALLOW_SIGN_UP=false \
    GF_INSTALL_PLUGINS=
