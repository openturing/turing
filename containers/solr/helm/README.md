```shell
helm repo add bitnami https://charts.bitnami.com/bitnami
helm install search-service-solr -f values.yaml bitnami/solr

kubectl port-forward --namespace default --address 0.0.0.0 svc/search-service-solr 8983:8983

sh ../../configure-sn.sh 192.168.157.1

```