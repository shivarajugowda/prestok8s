


# rm -rf ~/.helm ;  helm init --upgrade --service-account=tiller
# time helm install --name my-presto --wait ./presto 
# time helm install --name my-presto --wait ./prestok8s
# time helm delete --purge my-presto

# KubeFWD
sudo kubefwd services -n default

# Deactivate Cluster
$ curl -X POST localhost:8080/gateway/backend/deactivate/my-presto-cluster1


# DataDog 
helm install --name datadog1 -f datadog-values.yaml  stable/datadog
helm delete --purge datadog1