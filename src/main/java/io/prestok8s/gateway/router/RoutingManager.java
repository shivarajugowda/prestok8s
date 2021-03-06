package io.prestok8s.gateway.router;

import com.google.common.base.Strings;
import io.prestok8s.gateway.config.ProxyBackendConfiguration;
import io.prestok8s.proxyserver.ProxyServerConfiguration;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.ws.rs.HttpMethod;

import lombok.extern.slf4j.Slf4j;

import org.ehcache.Cache;
import org.ehcache.PersistentCacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.config.units.MemoryUnit;

/**
 * This class performs health check, stats counts for each backend and provides a backend given
 * request object. Default implementation comes here.
 */
@Slf4j
public abstract class RoutingManager {
  private static final Random RANDOM = new Random();
  private final Cache<String, String> queryIdBackendCache;
  private ExecutorService executorService = Executors.newFixedThreadPool(5);
  private GatewayBackendManager gatewayBackendManager;
  private AtomicLong queryNum = new AtomicLong(0);

  public RoutingManager(GatewayBackendManager gatewayBackendManager, String cacheDataDir) {
    this.gatewayBackendManager = gatewayBackendManager;

    PersistentCacheManager persistentCacheManager =
        CacheManagerBuilder.newCacheManagerBuilder()
            .with(CacheManagerBuilder.persistence(new File(cacheDataDir, "queryIdBackendMapping")))
            .withCache(
                "queryIdBackendPersistentCache",
                CacheConfigurationBuilder.newCacheConfigurationBuilder(
                    String.class,
                    String.class,
                    ResourcePoolsBuilder.newResourcePoolsBuilder()
                        .heap(1000, EntryUnit.ENTRIES)
                        .offheap(100, MemoryUnit.MB)
                        .disk(1, MemoryUnit.GB, true)))
            .build(true);
    this.queryIdBackendCache =
        persistentCacheManager.getCache(
            "queryIdBackendPersistentCache", String.class, String.class);
  }

  public void setBackendForQueryId(String queryId, String backend) {
    queryIdBackendCache.put(queryId, backend);
  }

  /**
   * Performs routing to an adhoc backend.
   *
   * @return
   */
  public String provideAdhocBackend() {
    List<ProxyBackendConfiguration> backends = this.gatewayBackendManager.getActiveAdhocBackends();
    //int backendId = Math.abs(RANDOM.nextInt()) % backends.size();
    int backendId = (int) queryNum.incrementAndGet() % backends.size();

    // Pick backend based on # queries running per worker node.
//    double[] weights = new double[backends.size()];
//    for(int i=0; i<weights.length; i++){
//      ProxyBackendConfiguration backend = backends.get(i);
//      int numWorkers = backend.getNumWorkers();
//
//      if (numWorkers == 0){
//        weights[i] = 0.0;
//      } else {
//        weights[i] = (numWorkers*4 - backend.getRunningQueries()) * 1.0d;
//        if (weights[i] < 0)
//          weights[i] = 1.0d / Math.abs(weights[i]);
//      }
//    }
//    int backendId = pickWeightedRandomIdx(weights);
    return backends.get(backendId).getProxyTo();
  }

  static int pickWeightedRandomIdx(double[] weights) {
    double totalWeight = Arrays.stream(weights).sum();
    double random = Math.random() * totalWeight;
    for (int i = 0; i < weights.length; ++i) {
      random -= weights[i];
      if (random <= 0.0d)
        return i;
    }
    return -1; // Reaches here if array size is 0.
  }

  /**
   * Performs routing to a given cluster group. This falls back to an adhoc backend, if no scheduled
   * backend is found.
   *
   * @return
   */
  public String provideBackendForRoutingGroup(String routingGroup) {
    List<ProxyBackendConfiguration> backends =
            this.gatewayBackendManager.getActiveBackends(routingGroup);
    if (backends.isEmpty()) {
      return provideAdhocBackend();
    }
    int backendId = Math.abs(RANDOM.nextInt()) % backends.size();
    return backends.get(backendId).getProxyTo();
  }

  /**
   * Performs cache look up, if a backend not found, it checks with all backends and tries to find
   * out which backend has info about given query id.
   *
   * @param queryId
   * @return
   */
  public String findBackendForQueryId(String queryId) {
    String backendAddress = queryIdBackendCache.get(queryId);
    if (Strings.isNullOrEmpty(backendAddress)) {
      log.error("Could not find mapping for query id {}", queryId);
      // for now fall back to the first backend as default
      backendAddress = findBackendForUnknownQueryId(queryId);
    }
    return backendAddress;
  }

  /**
   * This tries to find out which backend may have info about given query id. If not found returns
   * the first healthy backend.
   *
   * @param queryId
   * @return
   */
  private String findBackendForUnknownQueryId(String queryId) {
    List<ProxyBackendConfiguration> backends = gatewayBackendManager.getAllBackends();

    Map<String, Future<Integer>> responseCodes = new HashMap<>();
    try {
      for (ProxyServerConfiguration backend : backends) {
        String target = backend.getProxyTo() + "/v1/query/" + queryId;

        Future<Integer> call =
            executorService.submit(
                () -> {
                  URL url = new URL(target);
                  HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                  conn.setConnectTimeout((int) TimeUnit.SECONDS.toMillis(5));
                  conn.setReadTimeout((int) TimeUnit.SECONDS.toMillis(5));
                  conn.setRequestMethod(HttpMethod.HEAD);
                  return conn.getResponseCode();
                });
        responseCodes.put(backend.getProxyTo(), call);
      }
      for (Map.Entry<String, Future<Integer>> entry : responseCodes.entrySet()) {
        if (entry.getValue().isDone()) {
          int responseCode = entry.getValue().get();
          if (responseCode == 200) {
            log.info("Found query [{}] on backend [{}]", queryId, entry.getKey());
            setBackendForQueryId(queryId, entry.getKey());
            return entry.getKey();
          }
        }
      }
    } catch (Exception e) {
      log.warn("Query id [{}] not found", queryId);
    }
    // Fallback on first active backend if queryId mapping not found.
    return gatewayBackendManager.getActiveAdhocBackends().get(0).getProxyTo();
  }
}
