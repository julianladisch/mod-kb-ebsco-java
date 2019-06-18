package org.folio.service.holdings;

import static org.folio.repository.holdings.LoadStatus.COMPLETED;
import static org.folio.repository.holdings.LoadStatus.IN_PROGRESS;

import java.util.concurrent.CompletableFuture;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import org.glassfish.jersey.internal.util.Producer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import org.folio.holdingsiq.model.HoldingsLoadStatus;
import org.folio.holdingsiq.service.LoadService;
import org.folio.holdingsiq.service.impl.LoadServiceImpl;
import org.folio.repository.holdings.HoldingConstants;
import org.folio.repository.holdings.LoadStatus;

@Component
public class LoadServiceFacadeImpl implements LoadServiceFacade {
  private static final int MAX_COUNT = 5000;
  private static final Logger logger = LoggerFactory.getLogger(LoadServiceFacadeImpl.class);
  private final HoldingsService holdingsService;
  private long delay;
  private int statusRetryCount;
  private long snapshotRetryDelay;
  private int snapshotRetryCount;
  private Vertx vertx;

  public LoadServiceFacadeImpl(@Value("${holdings.status.check.delay}") long delay,
                               @Value("${holdings.status.retry.count}") int statusRetryCount,
                               @Value("${holdings.snapshot.retry.delay}") long snapshotRetryDelay,
                               @Value("${holdings.snapshot.retry.count}") int snapshotRetryCount,
                               Vertx vertx) {
    this.delay = delay;
    this.statusRetryCount = statusRetryCount;
    this.snapshotRetryDelay = snapshotRetryDelay;
    this.snapshotRetryCount = snapshotRetryCount;
    this.vertx = vertx;
    holdingsService = HoldingsService.createProxy(vertx, HoldingConstants.HOLDINGS_SERVICE_ADDRESS);
  }

  @Override
  public void createSnapshot(ConfigurationMessage configuration) {
    LoadServiceImpl loadingService = new LoadServiceImpl(configuration.getConfiguration(), vertx);
    retryOnFailure(snapshotRetryCount, snapshotRetryDelay, () ->
      populateHoldings(loadingService)
        .thenCompose(isSuccessful -> waitForCompleteStatus(statusRetryCount, loadingService))
        .thenApply(status -> {
          holdingsService.snapshotCreated(configuration);
          return null;
        }))
    .exceptionally(throwable -> {
      logger.error("Failed to create snapshot after " + snapshotRetryCount + " retries");
      return null;
    });
  }

  @Override
  public void startLoading(ConfigurationMessage configuration) {
    LoadServiceImpl loadingService = new LoadServiceImpl(configuration.getConfiguration(), vertx);

    getLoadingStatus(loadingService)
      .thenAccept(status -> {
        Integer totalCount = status.getTotalCount();
        loadHoldings(totalCount, configuration.getTenantId(), loadingService);
      });
  }

  private CompletableFuture<Void> populateHoldings(LoadService loadingService) {
    return getLoadingStatus(loadingService).thenCompose(loadStatus -> {
      final LoadStatus other = LoadStatus.fromValue(loadStatus.getStatus());
      if (IN_PROGRESS.equals(other)) {
//        holdingsStatusRepository.update()
        return CompletableFuture.completedFuture(null);
      } else {
        logger.info("Start populating holdings to stage environment.");
//        holdingsStatusRepository.update()
        return loadingService.populateHoldings();
      }
    });
  }

  public CompletableFuture<HoldingsLoadStatus> waitForCompleteStatus(int retryCount, LoadService loadingService) {
    CompletableFuture<HoldingsLoadStatus> future = new CompletableFuture<>();
    waitForCompleteStatus(retryCount, future, loadingService);
    return future;
  }

  public void waitForCompleteStatus(int retries, CompletableFuture<HoldingsLoadStatus> future, LoadService loadingService) {
    vertx.setTimer(delay, timerId -> getLoadingStatus(loadingService)
      .thenAccept(loadStatus -> {
        final LoadStatus status = LoadStatus.fromValue(loadStatus.getStatus());
        logger.info("Getting status of stage snapshot: {}.", status);
        if (COMPLETED.equals(status)) {
          future.complete(loadStatus);
        } else if (IN_PROGRESS.equals(status)) {
          if (retries <= 1) {
            throw new IllegalStateException("Failed to get status with status response:" + loadStatus);
          }
          waitForCompleteStatus(retries - 1, future, loadingService);
        } else {
          future.completeExceptionally(new IllegalStateException("Failed to get status with status response:" + loadStatus));
        }
      }).exceptionally(throwable -> {
        future.completeExceptionally(throwable);
        return null;
      }));
  }

  private CompletableFuture<HoldingsLoadStatus> getLoadingStatus(LoadService loadingService) {
    return loadingService.getLoadingStatus();
  }

  public void loadHoldings(Integer totalCount, String tenantId, LoadService loadingService) {
    CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
    final int totalRequestCount = getRequestCount(totalCount);
    for (int iteration = 1; iteration < totalRequestCount + 1; iteration++) {
      int finalIteration = iteration;
      future = future
        .thenCompose(o -> loadingService.loadHoldings(MAX_COUNT, finalIteration))
        .thenAccept(holdings -> holdingsService.saveHolding(new HoldingsMessage(holdings.getHoldingsList()), tenantId));
    }
  }

  public <T> CompletableFuture<T> retryOnFailure(int retries, long delay, Producer<CompletableFuture<T>> futureProducer) {
    CompletableFuture<T> future = new CompletableFuture<>();
    futureProducer.call()
      .thenAccept(future::complete)
      .exceptionally(throwable -> {
        if (retries > 1) {
          logger.error("Failed to create snapshot, will retry in " + delay + " milliseconds");
          vertx.setTimer(delay, timerId -> retryOnFailure(retries - 1, delay, futureProducer));
        } else {
          future.completeExceptionally(new IllegalStateException());
        }
        return null;
      });
    return future;
  }

  /**
   * Defines an amount of request needed to load all holdings from the staged area
   *
   * @param totalCount - total records count
   * @return number of requests
   */
  private int getRequestCount(Integer totalCount) {
    final int quotient = totalCount / MAX_COUNT;
    final int remainder = totalCount % MAX_COUNT;
    return remainder == 0 ? quotient : quotient + 1;
  }

}
