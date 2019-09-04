package io.prestok8s.gateway.router;

import io.prestok8s.gateway.config.ProxyBackendConfiguration;
import lombok.Data;
import lombok.ToString;

import java.util.List;

public interface GatewayBackendManager {
  List<ProxyBackendConfiguration> getAllBackends();

  List<ProxyBackendConfiguration> getAllActiveBackends();

  List<ProxyBackendConfiguration> getAllDeActiveBackends();

  List<ProxyBackendConfiguration> getActiveAdhocBackends();

  List<ProxyBackendConfiguration> getActiveBackends(String routingGroup);

  void deactivateBackend(String backendName);

  void activateBackend(ProxyBackendConfiguration px);

  void removeBackend(String backendName);

  ProxyBackendConfiguration getBackend(String backendName);

  ClusterStats getClusterStats(ProxyBackendConfiguration backend);

  @Data
  @ToString
  class ClusterStats {
    public String name;
    public int runningQueryCount;
    public int queuedQueryCount;
    public int blockedQueryCount;
    public int numWorkerNodes;
    public boolean healthy;
    public String clusterId;
  }
}
