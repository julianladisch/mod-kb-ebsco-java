package org.folio.service.holdings;

import java.util.Map;

import org.folio.rest.util.template.RMAPITemplateFactory;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Vertx;

@ProxyGen
public interface LoadServiceFacade {
  static LoadServiceFacade create(Vertx vertx, RMAPITemplateFactory templateFactory) {
    return new LoadServiceFacadeImpl(vertx, templateFactory);
  }

  static LoadServiceFacade createProxy(Vertx vertx, String address) {
    return new LoadServiceFacadeVertxEBProxy(vertx, address);
  }

  void createSnapshot(Map<String, String> okapiHeaders);

  void startLoading(Map<String, String> okapiHeaders);
}
