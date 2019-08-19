package io.prestok8s.gateway.k8s;

import io.fabric8.kubernetes.api.model.Service;
import io.prestok8s.gateway.config.ProxyBackendConfiguration;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodStatus;
import io.fabric8.kubernetes.client.*;
import io.fabric8.kubernetes.client.internal.readiness.Readiness;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class K8sClient implements Runnable {
    ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    public static String PRESTO_SELECTOR = "app=presto";
    private final ConcurrentMap<String, ProxyBackendConfiguration> backendMap;
    AtomicInteger portStart = new AtomicInteger(11000);

    public K8sClient(final ConcurrentMap<String, ProxyBackendConfiguration> backendMap) {
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

        ProxyBackendConfiguration px = new ProxyBackendConfiguration();
        int portNum = portStart.getAndIncrement();
        String cmd = "kubectl port-forward svc/" + serviceName
                + " -n " + nameSpace + " " + portNum + ":8080";

        try {
            Process process = Runtime.getRuntime().exec(cmd);
            px.setPortForward(process);
        } catch (IOException e) {
            e.printStackTrace();
        }

        px.setName(serviceName);
        px.setProxyTo("http://localhost:" + portNum);
        px.setRoutingGroup("adhoc");

        int freePort = 0;
        try(ServerSocket ss = new ServerSocket(freePort)) {
            freePort = ss.getLocalPort();
        } catch (IOException e) {
            e.printStackTrace();
        }
        px.setLocalPort(freePort);

        /*
        ProxyServer proxyServer = new ProxyServer(px, new ProxyHandler());
        proxyServer.start();
        px.setProxyServer(proxyServer);
        */
        backendMap.put(px.getName(), px);

        System.out.println("Added Presto Service to backend : " + serviceName);
    }

    private void removeBackend(String serviceName){
        ProxyBackendConfiguration px =  backendMap.remove(serviceName);
        if (px != null) {
            if (px.getProxyServer() != null)
                px.getProxyServer().close();
            if (px.getPortForward() != null)
                px.getPortForward().destroyForcibly();
        }

        System.out.println("Removed Presto Service from backends : " + serviceName);
    }
}
