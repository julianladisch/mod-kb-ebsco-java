package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.serviceproxy.ServiceBinder;

import org.folio.repository.holdings.HoldingConstants;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.service.holdings.HoldingsService;
import org.folio.service.holdings.LoadServiceFacade;
import org.folio.spring.SpringContextUtil;
import org.folio.spring.config.ApplicationConfig;

public class InitAPIImpl implements InitAPI{
  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    vertx.executeBlocking(
      future -> {
        SpringContextUtil.init(vertx, context, ApplicationConfig.class);
        new ServiceBinder(vertx)
          .setAddress(HoldingConstants.LOAD_FACADE_ADDRESS)
          .register(LoadServiceFacade.class, LoadServiceFacade.create(vertx));
        new ServiceBinder(vertx)
          .setAddress(HoldingConstants.HOLDINGS_SERVICE_ADDRESS)
          .register(HoldingsService.class, HoldingsService.create(vertx));
        future.complete();
      },
      result -> {
        if (result.succeeded()) {
          handler.handle(Future.succeededFuture(true));
        } else {
          handler.handle(Future.failedFuture(result.cause()));
        }
      });
  }
}
