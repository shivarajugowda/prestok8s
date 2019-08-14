package io.prestok8s.gateway.router.impl;

import io.prestok8s.gateway.router.GatewayBackendManager;
import io.prestok8s.gateway.router.RoutingManager;

public class DefaultRoutingManager extends RoutingManager {
  public DefaultRoutingManager(GatewayBackendManager gatewayBackendManager, String cacheDataDir) {
    super(gatewayBackendManager, cacheDataDir);
  }
}
