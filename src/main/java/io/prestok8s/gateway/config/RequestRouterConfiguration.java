package io.prestok8s.gateway.config;

import lombok.Data;

@Data
public class RequestRouterConfiguration {
  // Local gateway port
  private int port;

  // Name of the routing gateway name (for metrics purposes)
  private String name;

  // Cache dir to store query id <-> backend mapping
  private String cacheDir;

  private int historySize = 2000;
}
