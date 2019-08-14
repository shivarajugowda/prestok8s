package io.prestok8s.gateway;

import com.google.inject.Inject;
import io.prestok8s.proxyserver.ProxyServer;
import io.dropwizard.lifecycle.Managed;

public class GatewayManagedApp implements Managed {
  @Inject private ProxyServer gateway;

  @Override
  public void start() throws Exception {
    if (gateway != null) {
      gateway.start();
    }
  }

  @Override
  public void stop() throws Exception {
    if (gateway != null) {
      gateway.close();
    }
  }
}
