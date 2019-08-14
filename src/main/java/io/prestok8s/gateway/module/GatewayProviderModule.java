package io.prestok8s.gateway.module;

import com.codahale.metrics.Meter;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.prestok8s.baseapp.AppModule;
import io.prestok8s.gateway.config.GatewayConfiguration;
import io.prestok8s.gateway.config.RequestRouterConfiguration;
import io.prestok8s.gateway.handler.QueryIdCachingProxyHandler;
import io.prestok8s.gateway.router.GatewayBackendManager;
import io.prestok8s.gateway.router.QueryHistoryManager;
import io.prestok8s.gateway.router.impl.GatewayBackendManagerImpl;
import io.prestok8s.gateway.router.impl.QueryHistoryManagerImpl;
import io.prestok8s.proxyserver.ProxyHandler;
import io.prestok8s.proxyserver.ProxyServer;
import io.prestok8s.proxyserver.ProxyServerConfiguration;
import io.dropwizard.setup.Environment;

public class GatewayProviderModule extends AppModule<GatewayConfiguration, Environment> {

  private final GatewayBackendManager gatewayBackendManager;
  private final QueryHistoryManager queryHistoryManager;

  public GatewayProviderModule(GatewayConfiguration configuration, Environment environment) {
    super(configuration, environment);

    this.gatewayBackendManager =
        new GatewayBackendManagerImpl(configuration.getRequestRouter().getCacheDir());
    this.queryHistoryManager =
        new QueryHistoryManagerImpl(configuration.getRequestRouter().getHistorySize());

  }

  /* @return Provides instance of RoutingProxyHandler. */

  protected ProxyHandler getProxyHandler() {
    Meter requestMeter =
        getEnvironment()
            .metrics()
            .meter(getConfiguration().getRequestRouter().getName() + ".requests");
    // Return the Proxy Handler for RequestRouter.
    return new QueryIdCachingProxyHandler(
        gatewayBackendManager, queryHistoryManager, getConfiguration(), requestMeter);
  }

  protected ProxyServerConfiguration getGatewayProxyConfig() {
    RequestRouterConfiguration routerConfiguration = getConfiguration().getRequestRouter();

    ProxyServerConfiguration routerProxyConfig = new ProxyServerConfiguration();
    routerProxyConfig.setLocalPort(routerConfiguration.getPort());
    routerProxyConfig.setName(routerConfiguration.getName());
    routerProxyConfig.setProxyTo("");
    return routerProxyConfig;
  }

  @Provides
  @Singleton
  public ProxyServer provideGateway() {
    ProxyServer gateway = null;
    if (getConfiguration().getRequestRouter() != null) {
      // Setting up request router
      RequestRouterConfiguration routerConfiguration = getConfiguration().getRequestRouter();

      ProxyServerConfiguration routerProxyConfig = new ProxyServerConfiguration();
      routerProxyConfig.setLocalPort(routerConfiguration.getPort());
      routerProxyConfig.setName(routerConfiguration.getName());
      routerProxyConfig.setProxyTo("");

      ProxyHandler proxyHandler = getProxyHandler();
      gateway = new ProxyServer(routerProxyConfig, proxyHandler);
    }
    return gateway;
  }

  @Provides
  @Singleton
  public GatewayBackendManager getGatewayBackendManager() {
    return this.gatewayBackendManager;
  }

  @Provides
  @Singleton
  public QueryHistoryManager getQueryHistoryManager() {
    return this.queryHistoryManager;
  }
}
