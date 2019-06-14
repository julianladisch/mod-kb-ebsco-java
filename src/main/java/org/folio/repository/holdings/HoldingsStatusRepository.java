package org.folio.repository.holdings;

import java.util.concurrent.CompletableFuture;

import org.folio.rest.jaxrs.model.HoldingsLoadingStatus;

public interface HoldingsStatusRepository {

//  CompletableFuture<HoldingsLoadingStatus> get(String tenantId);

  CompletableFuture<Void> update(HoldingsLoadingStatus loadStatus, String tenantId);

}
