CLUSTER_NAME=my-cluster

### Google GKE Settings
GKE_PROJECT_ID=$(shell gcloud config list project --format=flattened | awk 'FNR == 1 {print $$2}')
GKE_ZONE=us-west1-b
GKE_USER=$(shell gcloud config get-value core/account)

### AWS EKS Settings
EKS_REGION=us-east-1

gke-create-cluster:
	gcloud container --project "$(GKE_PROJECT_ID)" clusters create "$(CLUSTER_NAME)" --zone "$(GKE_ZONE)" --machine-type "n1-standard-1" --min-nodes "1" --max-nodes "2" --image-type "COS" --enable-autoscaling --disk-size "50" --scopes "https://www.googleapis.com/auth/compute","https://www.googleapis.com/auth/devstorage.read_only","https://www.googleapis.com/auth/logging.write","https://www.googleapis.com/auth/monitoring","https://www.googleapis.com/auth/servicecontrol","https://www.googleapis.com/auth/service.management.readonly","https://www.googleapis.com/auth/trace.append" --network "default" --enable-cloud-monitoring --cluster-version=1.13 --addons HorizontalPodAutoscaling,HttpLoadBalancing --no-enable-autoupgrade
	# Dedicated Presto Nodepool with taints
	gcloud container node-pools create presto-node-pool --cluster "$(CLUSTER_NAME)" --zone "$(GKE_ZONE)" --node-taints special=presto:NoExecute --machine-type "n1-standard-4" --num-nodes "1" --min-nodes "1" --max-nodes "4" --image-type "COS" --enable-autoscaling --disk-size "50" --no-enable-autoupgrade
	# Service authorization for myself to debug.
	kubectl create clusterrolebinding cluster-admin-binding --clusterrole=cluster-admin --user=$(GKE_USER)

gke-delete-cluster:
	gcloud container clusters delete "$(CLUSTER_NAME)" --zone "$(GKE_ZONE)" --quiet

eks-create-cluster:
	eksctl create cluster --region=$(EKS_REGION) --name=$(CLUSTER_NAME)

eks-delete-cluster:
	eksctl delete cluster --region=${EKS_REGION} --name=$(CLUSTER_NAME)

setup-k8s:
	# Service account for Helm Tiller
	kubectl create serviceaccount tiller --namespace kube-system
	kubectl create clusterrolebinding tiller-cluster-rule --clusterrole=cluster-admin --serviceaccount=kube-system:tiller
	# Service account for Presto
	kubectl create serviceaccount prestosvcact --namespace default
	kubectl create clusterrolebinding presto-admin-binding --clusterrole=cluster-admin --serviceaccount=default:prestosvcact
	# Init Helm.
	rm -rf ~/.helm ;  helm init --upgrade --service-account=tiller

clean-k8s:
	# Service account for Helm Tiller
	kubectl delete serviceaccount tiller --namespace kube-system --ignore-not-found
	kubectl delete clusterrolebinding tiller-cluster-rule --ignore-not-found
	# Service account for Presto
	kubectl delete serviceaccount prestosvcact --namespace default --ignore-not-found
	kubectl delete clusterrolebinding presto-admin-binding --ignore-not-found
	# Install WeaveScope UI
	kubectl delete -f "https://cloud.weave.works/k8s/v1.13/scope.yaml"







