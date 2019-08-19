package io.prestok8s;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

public class TestK8sClient {
    public static void listPrestoServices() {
        try {
            KubernetesClient client = new DefaultKubernetesClient();
            List<Service> services = client.services().withLabel("app=presto").list().getItems();
            for(Service service : services) {
                service.getMetadata().getLabels();
                System.out.println(" Service name " + service.getMetadata().getName());
            }

            client.close();
        } catch (Throwable th){
            System.out.println("Kubernetes Hook FAILED : " + th);
        }
    }

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
        long start = System.currentTimeMillis();
        listPrestoServices();
        System.out.println("\n -- Total time taken : " + (System.currentTimeMillis() - start));
    }
}
