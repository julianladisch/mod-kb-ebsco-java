package org.folio.repository.holdings;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface HoldingsRepository {

  CompletableFuture<Void> save(List<DbHolding> holding, String tenantId);

  CompletableFuture<Void> deleteAll(String tenantId);

  CompletableFuture<List<DbHolding>> getByIds(String tenantId, List<String> resourceIds);
}
