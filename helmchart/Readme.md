

kubectl config get-contexts
kubectl config use-context CONTEXT_NAME

      env: 
      - name: AWS_PROFILE
        value: engineering
        

time helm install my-presto ./presto --wait --set server.workers=1
time helm install my-prestok8s ./prestok8s --wait 
time helm uninstall my-presto

# KubeFWD
sudo kubefwd services -n default

# Deactivate Cluster
$ curl -X POST localhost:9080/gateway/backend/deactivate/my-presto


# DataDog 
helm install datadog1  stable/datadog -f datadog-values.yaml 
helm delete --purge datadog1