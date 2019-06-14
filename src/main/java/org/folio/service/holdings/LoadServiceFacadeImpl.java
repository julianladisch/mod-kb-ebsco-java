package org.folio.service.holdings;

import java.util.Map;

import org.folio.rest.util.template.RMAPITemplateFactory;

import io.vertx.core.Vertx;

public class LoadServiceFacadeImpl implements LoadServiceFacade {

  private Vertx vertx;

  private RMAPITemplateFactory templateFactory;
  private final HoldingsService holdingsService;

  public LoadServiceFacadeImpl(Vertx vertx, RMAPITemplateFactory templateFactory) {
    this.templateFactory = templateFactory;
    //TODO set correct address
    String address = "address";
    holdingsService = HoldingsService.createProxy(vertx, address);
  }

  @Override
  public void createSnapshot(Map<String, String> okapiHeaders) {
//    templateFactory.createTemplate()
    //TODO implement

    holdingsService.snapshotCreated(null);
  }

  @Override
  public void startLoading(Map<String, String> okapiHeaders) {
    //TODO implement
  }
}
