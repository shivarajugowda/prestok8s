package io.prestok8s.gateway.router.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;

import io.prestok8s.gateway.config.ProxyBackendConfiguration;
import io.prestok8s.gateway.handler.QueryIdCachingProxyHandler;
import io.prestok8s.gateway.k8s.K8sPrestoClusterMonitor;
import io.prestok8s.gateway.router.GatewayBackendManager;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;

import javax.ws.rs.HttpMethod;

@Slf4j
public class GatewayBackendManagerImpl implements GatewayBackendManager {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String PRESTO_CLUSTER_STATE_FILE = "presto_cluster_state.json";

    private final ConcurrentMap<String, ProxyBackendConfiguration> backendMap;
    private final String cacheDir;
    private final K8sPrestoClusterMonitor k8SPrestoClusterMonitor;

    public GatewayBackendManagerImpl(String cacheDir) {
        this.cacheDir = cacheDir;
        this.backendMap = new ConcurrentHashMap<>(); // immutable / un-modifiable
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter();

        k8SPrestoClusterMonitor = new K8sPrestoClusterMonitor(this);
        //reloadClusterStateAtStartUp();
    }

    public List<ProxyBackendConfiguration> getAllBackends() {
        return ImmutableList.copyOf(backendMap.values());
    }

    public List<ProxyBackendConfiguration> getActiveAdhocBackends() {
        return getActiveBackends(QueryIdCachingProxyHandler.ADHOC_ROUTING_GROUP);
    }

    public List<ProxyBackendConfiguration> getAllActiveBackends() {
        return backendMap
                .values()
                .stream()
                .filter(backend -> backend.isActive())
                .collect(Collectors.toList());
    }

    public List<ProxyBackendConfiguration> getAllDeActiveBackends() {
        return backendMap
                .values()
                .stream()
                .filter(backend -> !backend.isActive())
                .collect(Collectors.toList());
    }

    public List<ProxyBackendConfiguration> getActiveBackends(String routingGroup) {
        return backendMap
                .values()
                .stream()
                .filter(backend -> backend.isActive())
                .filter(backend -> backend.getRoutingGroup().equalsIgnoreCase(routingGroup))
                .collect(Collectors.toList());
    }

    public void removeBackend(String backendName) {
        backendMap.remove(backendName);
    }

    public ProxyBackendConfiguration getBackend(String backendName) {
        return backendMap.get(backendName);
    }

    public void deactivateBackend(String backendName) {
        if (!backendMap.containsKey(backendName)) {
            throw new IllegalArgumentException("Backend name [" + backendName + "] not found");
        }
        ProxyBackendConfiguration backendToRemove = backendMap.get(backendName);

        if (!backendToRemove.isActive())
            return;

        backendToRemove.setActive(false);
        //persistClusterState();
        log.info("De-activating backend cluster [{}]", backendName);
    }

    public void activateBackend(ProxyBackendConfiguration backend) {
        if (backendMap.containsKey(backend.getName()))
            return;

        backend.setActive(true);
        backendMap.put(backend.getName(), backend);
        //persistClusterState();
        log.info("Activating backend cluster [{}]", backend.getName());
    }

    private synchronized void persistClusterState() {
        try (FileWriter fileWriter = new FileWriter(cacheDir + "/" + PRESTO_CLUSTER_STATE_FILE)) {
            String prestoClusterStateJson = OBJECT_MAPPER.writeValueAsString(backendMap);
            fileWriter.write(prestoClusterStateJson);
        } catch (Exception e) {
            log.error("Error saving the cluster state", e);
        }
    }

    private void reloadClusterStateAtStartUp() {
        try (FileReader fileReader = new FileReader(cacheDir + "/" + PRESTO_CLUSTER_STATE_FILE)) {
            StringBuffer sb = new StringBuffer();
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            String prestoClusterStateJson = sb.toString();

            Map<String, ProxyBackendConfiguration> previousClusterStateMap =
                    OBJECT_MAPPER.readValue(
                            prestoClusterStateJson,
                            new TypeReference<Map<String, ProxyBackendConfiguration>>() {
                            });
            previousClusterStateMap.forEach(
                    (k, v) -> {
                        if (backendMap.containsKey(k)) {
                            log.info(
                                    "Restoring from previous cluster state : cluster[{}] state [{}]",
                                    k,
                                    v.isActive());
                            backendMap.get(k).setActive(v.isActive());
                        }
                    });
        } catch (Exception e) {
            log.warn("No previous backend cluster state found - " + e.getMessage());
        }
    }

    public ClusterStats getClusterStats(ProxyBackendConfiguration backend) {
        ClusterStats clusterStats = new ClusterStats();
        clusterStats.setClusterId(backend.getName());
        final int BACKEND_CONNECT_TIMEOUT_SECONDS = 15;


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

}
