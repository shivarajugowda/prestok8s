package io.prestok8s.gateway.k8s;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import io.dropwizard.lifecycle.Managed;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.prestok8s.gateway.config.ProxyBackendConfiguration;
import io.prestok8s.gateway.router.GatewayBackendManager;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.HttpMethod;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public class K8sPrestoAutoScaler implements Managed {
    private static final Logger logger = LoggerFactory.getLogger(K8sPrestoAutoScaler.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int BACKEND_CONNECT_TIMEOUT_SECONDS = 15;
    private static final int MAX_THRESHOLD_QUEUED_QUERY_COUNT = 100;

    private static final int MONITOR_TASK_DELAY_SECONDS = 10;

    private GatewayBackendManager gatewayBackendManager;

    private volatile boolean monitorActive = true;

    private ExecutorService executorService = Executors.newFixedThreadPool(10);
    private ExecutorService singleTaskExecutor = Executors.newSingleThreadExecutor();

    public K8sPrestoAutoScaler(final GatewayBackendManager gatewayBackendManager) {
        this.gatewayBackendManager = gatewayBackendManager;
        start();
    }

    public void start() {
        singleTaskExecutor.submit(
                () -> {
                    while (monitorActive) {
                        try {
                            // ACTIVE Clusters
                            List<ProxyBackendConfiguration> activeClusters =
                                    gatewayBackendManager.getAllActiveBackends();
                            List<Future<ClusterStats>> futures = new ArrayList<>();
                            for (ProxyBackendConfiguration backend : activeClusters) {
                                executorService.submit(() -> handleAutoScale(backend));
                            }

                            // DeACTIVE Clusters
                            List<ProxyBackendConfiguration> deActiveClusters =
                                    gatewayBackendManager.getAllDeActiveBackends();
                            for (ProxyBackendConfiguration backend : deActiveClusters) {
                                executorService.submit(() -> handleDeActiveCluster(backend));
                            }
                        } catch (Exception e) {
                            log.error("Error performing backend monitor tasks", e);
                        }
                        try {
                            Thread.sleep(TimeUnit.SECONDS.toMillis(MONITOR_TASK_DELAY_SECONDS));
                        } catch (Exception e) {
                            log.error("Error with monitor task", e);
                        }
                    }
                });
    }

    private void handleAutoScale(ProxyBackendConfiguration backend) {
        ClusterStats clusterStats = getPrestoClusterStats(backend);
        String svcName = clusterStats.name;
        int queryCount = clusterStats.runningQueryCount;

        // Update status
        backend.setNumWorkers(clusterStats.numWorkerNodes);
        backend.setRunningQueries(clusterStats.runningQueryCount);

        int expectedReplicas = Math.max(1, queryCount / 2);
        expectedReplicas = Math.min(30, expectedReplicas);
        String depName = "";
        int replicas = 0;

        try {
            KubernetesClient client = new DefaultKubernetesClient();
            List<Deployment> deps = client.apps().deployments()
                    .withLabel("app=presto")
                    .withLabel("component=worker")
                    .withLabel("release=" + svcName)
                    .list().getItems();

            // There might bo only cooordinator clusters.
            if (deps.size() == 1) {
                Deployment dep = deps.get(0);

                depName = dep.getMetadata().getName();
                String depNameSpace = dep.getMetadata().getNamespace();
                replicas = dep.getSpec().getReplicas();

                if (replicas != expectedReplicas) {
                    logger.info(" Deployment : " + depName + " Current workers : " + replicas + " Expected workers : " + expectedReplicas);
                    client.apps().deployments().inNamespace(depNameSpace).withName(depName).edit().editSpec().withReplicas(expectedReplicas).endSpec().done();
                }
            } else if (deps.size() == 0){
                // Do nothing. Only coordinator nodes deployed.
            } else {
                logger.error("Found unexpected number of worker deployments. Expecting 1 but found " + deps.size());
            }
            client.close();
        } catch (Throwable th) {
          logger.error("Failed to AutoScale Workers for Deployment : " + depName + " Current workers : " + replicas
                  + " Expected workers : " + expectedReplicas  + th);

        }
    }

    private void handleDeActiveCluster(ProxyBackendConfiguration backend) {
        ClusterStats clusterStats = getPrestoClusterStats(backend);

        if(clusterStats.runningQueryCount == 0) {
            String[] deleteCmd = {"helm", "delete", "--purge", backend.getName()};

            try {
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
                    log.info("Deleted cluster : " + backend.getName() +  " " + output);
                } else {
                    log.error("Error deleting cluster : " + backend.getName() +  " " + output + " " + errorString);
                }
            } catch (IOException | InterruptedException e) {
                log.error("Error deleting cluster : " + backend.getName() +  " " + e);
            }
        } else {
            log.info("Backend " + backend.getName() +  " has " + clusterStats.runningQueryCount + " queries running, holding off on deletion, will retry in a few seconds ");
        }
    }
    private ClusterStats getPrestoClusterStats(ProxyBackendConfiguration backend) {
        ClusterStats clusterStats = new ClusterStats();
        clusterStats.setClusterId(backend.getName());

        String target = backend.getProxyTo() + "/v1/cluster";
        try {
            URL url = new URL(target);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(BACKEND_CONNECT_TIMEOUT_SECONDS));
            conn.setReadTimeout((int) TimeUnit.SECONDS.toMillis(BACKEND_CONNECT_TIMEOUT_SECONDS));
            conn.setRequestMethod(HttpMethod.GET);
            conn.connect();
            if (conn.getResponseCode() == HttpStatus.SC_OK) {
                clusterStats.setName(backend.getName());
                clusterStats.setHealthy(true);
                BufferedReader reader =
                        new BufferedReader(new InputStreamReader((InputStream) conn.getContent()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line + "\n");
                }
                HashMap<String, Object> result = OBJECT_MAPPER.readValue(sb.toString(), HashMap.class);
                clusterStats.setNumWorkerNodes((int) result.get("activeWorkers"));
                clusterStats.setQueuedQueryCount((int) result.get("queuedQueries"));
                clusterStats.setRunningQueryCount((int) result.get("runningQueries"));
                clusterStats.setBlockedQueryCount((int) result.get("blockedQueries"));
                conn.disconnect();
            }
        } catch (Exception e) {
            log.error("Error fetching cluster stats from [" + target + "]", e);
        }
        return clusterStats;
    }

    public void stop() throws Exception {
        this.monitorActive = false;
        this.executorService.shutdown();
        this.singleTaskExecutor.shutdown();
    }

    @Data
    @ToString
    public static class ClusterStats {
        private String name;
        private int runningQueryCount;
        private int queuedQueryCount;
        private int blockedQueryCount;
        private int numWorkerNodes;
        private boolean healthy;
        private String clusterId;
    }
}
