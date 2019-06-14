package org.folio.service.holdings;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Vertx;

@ProxyGen
public interface LoadServiceFacade {
  static LoadServiceFacade create(Vertx vertx) {
    return new LoadServiceFacadeImpl(vertx);
  }

  static LoadServiceFacade createProxy(Vertx vertx, String address) {
    return new LoadServiceFacadeVertxEBProxy(vertx, address);
  }

  void createSnapshot(ConfigurationMessage configuration);

  void startLoading(ConfigurationMessage configuration);
}
