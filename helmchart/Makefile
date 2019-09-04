CLUSTER_NAME=shiv

### Google GKE Settings
GKE_PROJECT_ID=$(shell gcloud config list project --format=flattened | awk 'FNR == 1 {print $$2}')
GKE_ZONE=us-west1-b
GKE_USER=$(shell gcloud config get-value core/account)

### AWS EKS Settings
EKS_REGION=us-east-1

gke-create-cluster:
	gcloud container --project "$(GKE_PROJECT_ID)" clusters create "$(CLUSTER_NAME)" --zone "$(GKE_ZONE)" --machine-type "n1-standard-1" --min-nodes "1" --max-nodes "2" --image-type "COS" --enable-autoscaling --disk-size "25" --scopes "https://www.googleapis.com/auth/compute","https://www.googleapis.com/auth/devstorage.read_only","https://www.googleapis.com/auth/logging.write","https://www.googleapis.com/auth/monitoring","https://www.googleapis.com/auth/servicecontrol","https://www.googleapis.com/auth/service.management.readonly","https://www.googleapis.com/auth/trace.append" --network "default" --enable-cloud-monitoring --cluster-version=1.13.7-gke.24 --addons HorizontalPodAutoscaling,HttpLoadBalancing --no-enable-autoupgrade --no-enable-cloud-logging --no-enable-cloud-monitoring
	# Dedicated Presto Nodepool with taints
	gcloud container node-pools create presto-node-pool --cluster "$(CLUSTER_NAME)" --zone "$(GKE_ZONE)" --node-taints special=presto:NoExecute --machine-type "n1-standard-4" --num-nodes "3" --min-nodes "3" --max-nodes "10" --image-type "COS" --enable-autoscaling --disk-size "30" --no-enable-autoupgrade
	# Service account for Presto
	kubectl create serviceaccount prestosvcact --namespace default
	kubectl create clusterrolebinding presto-admin-binding --clusterrole=cluster-admin --serviceaccount=default:prestosvcact
	# Service authorization for myself to debug.
	kubectl create clusterrolebinding cluster-admin-binding --clusterrole=cluster-admin --user=$(GKE_USER)

gke-delete-cluster:
	gcloud container clusters delete "$(CLUSTER_NAME)" --zone "$(GKE_ZONE)" --quiet

eks-create-cluster:
	export AWS_PROFILE=engineering; eksctl create cluster  --name=$(CLUSTER_NAME) --node-type t3.xlarge --nodes 4  --nodes-min 4  --nodes-max 4
	# Service account for Presto
	kubectl create serviceaccount prestosvcact --namespace default
	kubectl create clusterrolebinding presto-admin-binding --clusterrole=cluster-admin --serviceaccount=default:prestosvcact

eks-delete-cluster:
	export AWS_PROFILE=engineering; eksctl delete cluster  --name=$(CLUSTER_NAME)








