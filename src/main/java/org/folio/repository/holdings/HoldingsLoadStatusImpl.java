package org.folio.repository.holdings;

import static org.folio.common.FutureUtils.mapVertxFuture;
import static org.folio.repository.DbUtil.executeInTransaction;
import static org.folio.repository.DbUtil.getHoldingsTableName;
import static org.folio.tag.repository.resources.HoldingsTableConstants.INSERT_OR_UPDATE_LOADING_STATUS;

import java.util.concurrent.CompletableFuture;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.UpdateResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.folio.rest.jaxrs.model.HoldingsLoadingStatus;

@Component
public class HoldingsLoadStatusImpl implements HoldingsStatusRepository {
  private static final Logger LOG = LoggerFactory.getLogger(HoldingsLoadStatusImpl.class);

  private Vertx vertx;

  @Autowired
  public HoldingsLoadStatusImpl(Vertx vertx) {
    this.vertx = vertx;
  }


  @Override
  public CompletableFuture<Void> update(HoldingsLoadingStatus status, String tenantId) {

    return executeInTransaction(tenantId, vertx, (postgresClient, connection) -> {
      final String query = String.format(INSERT_OR_UPDATE_LOADING_STATUS, getHoldingsTableName(tenantId));
      JsonArray parameters = new JsonArray().add(status);
      LOG.info("Do update/insert query = " + query);
      Future<UpdateResult> future = Future.future();
      postgresClient.execute(query, parameters, future.completer());
      return mapVertxFuture(future).thenApply(result -> null);
    });
  }
}
