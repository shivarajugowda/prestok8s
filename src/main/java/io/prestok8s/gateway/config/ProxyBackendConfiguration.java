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
  private ProxyServer proxyServer = null;
  private Process portForward = null;
}
