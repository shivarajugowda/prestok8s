package io.prestok8s;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TestK8sClient {
    public static void listPrestoServices() {
        try {
            KubernetesClient client = new DefaultKubernetesClient();
            List<Service> services = client.services().withLabel("app=presto").list().getItems();

            for(Service service : services) {
                String svcName = service.getMetadata().getName();
                System.out.println(" Service name " + svcName);


                List<Deployment> deps = client.apps().deployments()
                        .withLabel("app=presto")
                        .withLabel("component=worker")
                        .withLabel("release="+svcName)
                        .list().getItems();
                assert (deps.size() == 1);
                Deployment dep = deps.get(0);

                String depName  = dep.getMetadata().getName();
                String depNameSpace = dep.getMetadata().getNamespace();
                System.out.println(" Deployment name " + depName);
                System.out.println(" Namespace " + depNameSpace);
                System.out.println(" Replicas " + dep.getSpec().getReplicas());
                client.apps().deployments().inNamespace(depNameSpace).withName(depName).edit().editSpec().withReplicas(1).endSpec().done();

                Map<String, String> annotations = dep.getMetadata().getAnnotations();
                for(String key : annotations.keySet()){
                    System.out.println(" key :" + key + " value :" + annotations.get(key));
                }
            }

            client.close();
        } catch (Throwable th){
            System.out.println("Kubernetes Hook FAILED : " + th);
        }
    }

    public static void main(String[] args) throws IOException, ExecutionException, InterruptedException {
       // java.util.logging.Logger.getLogger("org.hibernate").setLevel(Level.OFF);
//        Logger rootLogger = LogManager.getLogManager().getLogger("");
//        rootLogger.setLevel(Level.INFO);
//        for (Handler h : rootLogger.getHandlers()) {
//            h.setLevel(Level.INFO);
//        }

        long start = System.currentTimeMillis();
        listPrestoServices();
        System.out.println("\n -- Total time taken : " + (System.currentTimeMillis() - start));
    }
}
