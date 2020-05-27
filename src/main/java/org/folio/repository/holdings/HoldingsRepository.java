package org.folio.repository.holdings;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface HoldingsRepository {

  CompletableFuture<Void> saveAll(Set<HoldingInfoInDB> holding, Instant updatedAt, String credentialsId, String tenantId);

  CompletableFuture<Void> deleteBeforeTimestamp(Instant timestamp, String credentialsId, String tenantId);

  CompletableFuture<List<HoldingInfoInDB>> findAllById(List<String> resourceIds, String credentialsId, String tenantId);

  CompletableFuture<Void> deleteAll(Set<HoldingsId> holdings, String credentialsId, String tenantId);
}
