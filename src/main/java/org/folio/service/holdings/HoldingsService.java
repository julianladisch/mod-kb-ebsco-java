package org.folio.service.holdings;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.folio.repository.holdings.DbHolding;
import org.folio.repository.holdings.HoldingsRepository;
import org.folio.repository.holdings.HoldingsStatusRepository;
import org.folio.repository.resources.DbResource;
import org.folio.rest.util.template.RMAPITemplateContext;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Vertx;

@ProxyGen
@VertxGen
public interface HoldingsService {
  @GenIgnore
  static HoldingsService create(Vertx vertx, HoldingsRepository repository, long delay,
                                int retryCount, HoldingsStatusRepository statusRepository) {
    return new HoldingsServiceImpl(vertx, repository, delay, retryCount, statusRepository);
  }

  @GenIgnore
  static HoldingsService createProxy(Vertx vertx, String address) {
    return new HoldingsServiceVertxEBProxy(vertx, address);
  }

  @GenIgnore
  default CompletableFuture<Void> loadHoldings(RMAPITemplateContext context, String tenantId) {
    //Default implementation is necessary for automatically generated vertx proxy
    throw new UnsupportedOperationException();
  }

  @GenIgnore
  default CompletableFuture<List<DbHolding>> getHoldingsByIds(String tenant, List<DbResource> resourcesResult) {
    throw new UnsupportedOperationException();
  }

  void saveHolding(HoldingsMessage holdings, String tenantId);

  void snapshotCreated(String tenantId);

}
