package io.prestok8s.gateway.config;

import io.prestok8s.proxyserver.ProxyServer;
import io.prestok8s.proxyserver.ProxyServerConfiguration;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true, exclude = {"active"})
public class ProxyBackendConfiguration extends ProxyServerConfiguration {
  private boolean includeInRouter = true;
  private boolean active = true;
  private String routingGroup = "adhoc";
  private int numWorkers = 0;
  private int runningQueries = 0;
  private Long coolDownStartTime = null;
}
