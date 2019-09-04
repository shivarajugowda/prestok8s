package io.prestok8s.gateway.k8s;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.prestok8s.gateway.config.ProxyBackendConfiguration;
import io.prestok8s.gateway.router.GatewayBackendManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class K8sPrestoClusterMonitor implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(K8sPrestoClusterMonitor.class);
    ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);

    public static String PRESTO_SELECTOR = "app=presto";
    private final GatewayBackendManager backendManager;

    public K8sPrestoClusterMonitor(final GatewayBackendManager backendManager) {
        this.backendManager = backendManager;
        scheduledExecutorService.scheduleAtFixedRate(this, 0, 5, TimeUnit.SECONDS);
    }

    public void run() {
        try (KubernetesClient client = new DefaultKubernetesClient()) {
            List<Service> services = client.services().withLabel(PRESTO_SELECTOR).list().getItems();

            // Handle deletions.
            List<String> serviceNames = services.stream().map(name -> name.getMetadata().getName()).collect(Collectors.toList());
            for (ProxyBackendConfiguration backend : backendManager.getAllBackends()) {
                if (!serviceNames.contains(backend.getName()))
                    backendManager.removeBackend(backend.getName());
            }

            // Handle additions.
            services.parallelStream().forEach(s -> this.handleBackend(client, s));
        } catch (Throwable th) {
            System.out.println("Cluster Monitor FAILED : " + th);
        }
    }

    private void handleBackend(KubernetesClient client, Service service) {
        String serviceName = service.getMetadata().getName();

        try {
            ProxyBackendConfiguration backend = backendManager.getBackend(serviceName);

            if (backend == null) {
                // New Backend available, add it.
                backend = new ProxyBackendConfiguration();
                int svcPort = service.getSpec().getPorts().get(0).getPort();

                backend.setName(serviceName);
                backend.setProxyTo("http://" + serviceName + ":" + svcPort);
                backend.setRoutingGroup("adhoc");
                backendManager.activateBackend(backend);
            }

            GatewayBackendManager.ClusterStats clusterStats = backendManager.getClusterStats(backend);
            backend.setNumWorkers(clusterStats.numWorkerNodes);
            backend.setRunningQueries(clusterStats.runningQueryCount);

            if (backend.isActive())
                handleAutoScale(client, backend);
            else
                deleteK8SCluster(backend);
        } catch (Throwable th) {
            logger.error("Failed to Handle Service : " + serviceName + th);
        }
    }

    private void handleAutoScale(KubernetesClient client, ProxyBackendConfiguration backend) {
        List<Deployment> deps = client.apps().deployments()
                .withLabel("app=presto")
                .withLabel("component=worker")
                .withLabel("release=" + backend.getName())
                .list().getItems();

        if (deps.size() == 0)
            return; // No Worker Deployments.

        assert (deps.size() == 1);
        Deployment dep = deps.get(0);

        AutoScaleConfig asconfig = new AutoScaleConfig(dep.getMetadata().getAnnotations());
        if (!asconfig.enabled)
            return;

        int currentWorkers = dep.getSpec().getReplicas();
        int expectedWorkers = Math.max(asconfig.minWorkers, backend.getRunningQueries() / asconfig.expectedQueriesPerWorker);
        expectedWorkers = Math.min(asconfig.maxWorkers, expectedWorkers);

        if (currentWorkers != expectedWorkers) {
            // Handle cool down period for scale down.
            if (expectedWorkers < currentWorkers) {
                if(backend.getCoolDownStartTime() == null)
                    backend.setCoolDownStartTime(System.currentTimeMillis());
                if ((System.currentTimeMillis() - backend.getCoolDownStartTime()) < asconfig.coolDownDelayInSecs * 1000)
                    return;
            }

            String depName = dep.getMetadata().getName();
            String depNameSpace = dep.getMetadata().getNamespace();
            logger.info(" Update Deployment Workers : " + depName + " Current workers : " + currentWorkers + " Expected workers : " + expectedWorkers);
            client.apps().deployments().inNamespace(depNameSpace).withName(depName).edit().editSpec().withReplicas(expectedWorkers).endSpec().done();
        }
        backend.setCoolDownStartTime(null);
    }


    private void deleteK8SCluster(ProxyBackendConfiguration backend) throws IOException, InterruptedException {
        if (backend.getRunningQueries() != 0) {
            logger.info("Backend " + backend.getName() + " has " + backend.getRunningQueries()
                    + " queries still running, holding off on deletion, will retry in a few seconds ");
            return;
        }

        String[] deleteCmd = {"helm", "uninstall", backend.getName()};

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(deleteCmd);
        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line + "\n");
        }

        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        StringBuilder errorString = new StringBuilder();
        while ((line = errorReader.readLine()) != null) {
            output.append(line + "\n");
        }

        int exitVal = process.waitFor();
        if (exitVal == 0) {
            logger.info("Deleted cluster : " + backend.getName() + " " + output);
        } else {
            logger.error("Error deleting cluster : " + backend.getName() + " " + output + " " + errorString);
        }
    }

    static class AutoScaleConfig {
        boolean enabled = false;
        int minWorkers = 1;
        int maxWorkers = 1;
        int coolDownDelayInSecs = 60;
        int expectedQueriesPerWorker = 4;

        public AutoScaleConfig(Map<String, String> annotations) {
            if (annotations == null)
                return;
            enabled = Boolean.valueOf(annotations.get("autoScale.enabled"));
            if(enabled) {
                minWorkers = getIntValue(annotations, "autoScale.minWorkers");
                maxWorkers = getIntValue(annotations, "autoScale.maxWorkers");
                coolDownDelayInSecs = getIntValue(annotations, "autoScale.coolDownDelayInSecs");
                expectedQueriesPerWorker = getIntValue(annotations, "autoScale.expectedQueriesPerWorker");
            }
        }

        public int getIntValue(Map<String, String> annotations, String key) {
            try {
                return Integer.valueOf(annotations.get(key));
            } catch (Throwable th){
                logger.error("Incorrect value specified for AutoScale Config : " + key + " Value : " + annotations.get(key) + th);
                throw th;
            }
        }

    }
}
