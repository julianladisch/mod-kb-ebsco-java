package org.folio.service.holdings;

import static org.folio.common.ListUtils.mapItems;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.folio.holdingsiq.model.Holding;
import org.folio.repository.holdings.DbHolding;
import org.folio.repository.holdings.HoldingConstants;
import org.folio.repository.holdings.HoldingsRepository;
import org.folio.repository.holdings.HoldingsStatusRepository;
import org.folio.repository.resources.DbResource;
import org.folio.rest.util.template.RMAPITemplateContext;
import org.folio.spring.SpringContextUtil;

@Component
public class HoldingsServiceImpl implements HoldingsService {

  private static final Logger logger = LoggerFactory.getLogger(HoldingsServiceImpl.class);
  private Vertx vertx;
  @Value("${holdings.status.check.delay}")
  private long delay;
  @Value("${holdings.status.retry.count}")
  private int retryCount;
  @Autowired
  private HoldingsRepository holdingsRepository;
  @Autowired
  private HoldingsStatusRepository holdingsStatusRepository;
  private final LoadServiceFacade loadServiceFacade;

  public HoldingsServiceImpl(Vertx vertx) {
    this.vertx = vertx;
    SpringContextUtil.autowireDependencies(this, vertx.getOrCreateContext());
    loadServiceFacade = LoadServiceFacade.createProxy(vertx, HoldingConstants.LOAD_FACADE_ADDRESS);
  }

  @Override
  public CompletableFuture<Void> loadHoldings(RMAPITemplateContext context) {
    loadServiceFacade.createSnapshot(new ConfigurationMessage(context.getConfiguration(), context.getOkapiData().getTenant()));
    return CompletableFuture.completedFuture(null);
  }

  @Override
  public CompletableFuture<List<DbHolding>> getHoldingsByIds(String tenant, List<DbResource> resourcesResult) {
    return holdingsRepository.getByIds(tenant, getTitleIdsAsList(resourcesResult));
  }

  private List<String> getTitleIdsAsList(List<DbResource> resources){
    return mapItems(resources, dbResource -> dbResource.getId().getProviderIdPart() + "-"
      + dbResource.getId().getPackageIdPart() + "-" + dbResource.getId().getTitleIdPart());
  }

  @Override
  public void saveHolding(HoldingsMessage holdings, String tenantId) {
    logger.info("Saving holdings to database.");
    holdingsRepository.save(mapItems(holdings.getHoldingList(), getHoldingMapping()), tenantId);
  }

  @Override
  public void snapshotCreated(ConfigurationMessage message) {
      loadServiceFacade.startLoading(message);
  }

  private Function<Holding, DbHolding> getHoldingMapping() {
    return holding -> new DbHolding(
      holding.getTitleId(),
      holding.getPackageId(),
      holding.getVendorId(),
      holding.getPublicationTitle(),
      holding.getPublisherName(),
      holding.getResourceType()
    );
  }
}
