package org.folio.service.holdings;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Vertx;

import org.folio.repository.holdings.DbHolding;
import org.folio.repository.resources.DbResource;
import org.folio.rest.util.template.RMAPITemplateContext;

@ProxyGen
@VertxGen
public interface HoldingsService {
  @GenIgnore
  static HoldingsService create(Vertx vertx) {
    return new HoldingsServiceImpl(vertx);
  }

  @GenIgnore
  static HoldingsService createProxy(Vertx vertx, String address) {
    return new HoldingsServiceVertxEBProxy(vertx, address);
  }

  @GenIgnore
  default CompletableFuture<Void> loadHoldings(RMAPITemplateContext context) {
    //Default implementation is necessary for automatically generated vertx proxy
    throw new UnsupportedOperationException();
  }

  @GenIgnore
  default CompletableFuture<List<DbHolding>> getHoldingsByIds(String tenant, List<DbResource> resourcesResult) {
    throw new UnsupportedOperationException();
  }

  void saveHolding(HoldingsMessage holdings, String tenantId);

  void snapshotCreated(ConfigurationMessage message);
}
