package org.folio.repository.holdings;

import static org.folio.common.FutureUtils.mapVertxFuture;
import static org.folio.common.ListUtils.createPlaceholders;
import static org.folio.repository.DbUtil.getHoldingsStatusTableName;
import static org.folio.tag.repository.resources.HoldingsTableConstants.INSERT_UPDATE_LOADING_STATUS;
import static org.folio.tag.repository.resources.HoldingsTableConstants.UPDATE_LOADING_STATUS;

import java.util.concurrent.CompletableFuture;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.UpdateResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.folio.rest.jaxrs.model.HoldingsLoadingStatus;
import org.folio.rest.persist.PostgresClient;

@Component
public class HoldingsStatusRepositoryImpl implements HoldingsStatusRepository {
  private static final Logger LOG = LoggerFactory.getLogger(HoldingsStatusRepositoryImpl.class);

  private Vertx vertx;

  @Autowired
  public HoldingsStatusRepositoryImpl(Vertx vertx) {
    this.vertx = vertx;
  }


  @Override
  public CompletableFuture<HoldingsLoadingStatus> get(String tenantId) {
    return null;
  }

  @Override
  public CompletableFuture<Void> update(HoldingsLoadingStatus status, String tenantId) {

    final String query = String.format(UPDATE_LOADING_STATUS, getHoldingsStatusTableName(tenantId), Json.encode(status));
    LOG.info("Do update/insert query = " + query);
    Future<UpdateResult> future = Future.future();
    PostgresClient postgresClient = PostgresClient.getInstance(vertx, tenantId);
    postgresClient.execute(query, future.completer());
    return mapVertxFuture(future).thenApply(result -> null);
  }

  @Override
  public CompletableFuture<Void> save(HoldingsLoadingStatus status, String tenantId) {
    final String query = String.format(INSERT_UPDATE_LOADING_STATUS, getHoldingsStatusTableName(tenantId), createPlaceholders(2));
    JsonArray parameters = new JsonArray().add("1b3d3881-656b-40e1-87ed-a98e2ae5732d").add(Json.encode(status)).add(Json.encode(status));
    LOG.info("Do insert query = " + query);
    Future<UpdateResult> future = Future.future();
    PostgresClient postgresClient = PostgresClient.getInstance(vertx, tenantId);
    postgresClient.execute(query, parameters, future.completer());
    return mapVertxFuture(future).thenApply(result -> null);
  }
}
