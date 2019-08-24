package io.prestok8s.gateway.k8s;

import io.fabric8.kubernetes.api.model.Service;
import io.prestok8s.gateway.config.ProxyBackendConfiguration;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.internal.readiness.Readiness;
import io.prestok8s.proxyserver.ProxyHandler;
import io.prestok8s.proxyserver.ProxyServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class K8sPrestoClusterMonitor implements Runnable {
    ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    public static String PRESTO_SELECTOR = "app=presto";
    private final ConcurrentMap<String, ProxyBackendConfiguration> backendMap;
    AtomicInteger portStart = new AtomicInteger(11000);
    private static String OS = System.getProperty("os.name").toLowerCase();

    public K8sPrestoClusterMonitor(final ConcurrentMap<String, ProxyBackendConfiguration> backendMap) {
        this.backendMap = backendMap;
        scheduledExecutorService.scheduleAtFixedRate(this, 0,5, TimeUnit.SECONDS);
    }

    public void run() {
        long start = System.currentTimeMillis();
        try {
            KubernetesClient client = new DefaultKubernetesClient();
            List<Service> services = client.services().withLabel(PRESTO_SELECTOR).list().getItems();

            // Handle deletions.
            List<String> serviceNames = services.stream().map(name -> name.getMetadata().getName()).collect(Collectors.toList());
            List<String> removedServiceNames = new ArrayList<>();
            for (String serviceName : backendMap.keySet()) {
                if (!serviceNames.contains(serviceName))
                    removedServiceNames.add(serviceName);
            }
            removedServiceNames.parallelStream().forEach(this::removeBackend);

            // Handle additions.
            services.parallelStream().forEach(this::addBackend);

            client.close();
        } catch (Throwable th){
            System.out.println("Kubernetes Hook FAILED : " + th);
        }
    }

    private void addBackend(Service service){
        String serviceName = service.getMetadata().getName();
        String nameSpace = service.getMetadata().getNamespace();

        if(backendMap.containsKey(service.getMetadata().getName()))
            return;

        int svcPort = service.getSpec().getPorts().get(0).getPort();
        ProxyBackendConfiguration px = new ProxyBackendConfiguration();
        String proxyTo = "http://" + serviceName + ":" + svcPort;

        px.setName(serviceName);
        px.setProxyTo(proxyTo);
        px.setRoutingGroup("adhoc");

        backendMap.put(px.getName(), px);

        System.out.println("Added Presto Service to backend : " + serviceName);
    }

    private void removeBackend(String serviceName){
        ProxyBackendConfiguration px =  backendMap.remove(serviceName);
        System.out.println("Removed Presto Service from backends : " + serviceName);
    }

    public static boolean isMac() {
        return (OS.indexOf("mac") >= 0);
    }
}
