package org.folio.service.holdings;

import static org.folio.repository.holdings.LoadStatus.COMPLETED;
import static org.folio.repository.holdings.LoadStatus.IN_PROGRESS;
import static org.folio.repository.holdings.LoadStatusUtils.getLoadStatusCompleted;
import static org.folio.repository.holdings.LoadStatusUtils.getLoadStatusInProgressLoadingHoldings;
import static org.folio.repository.holdings.LoadStatusUtils.getLoadStatusInProgressPopulatedToHoldings;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
import org.folio.repository.holdings.HoldingsStatusRepository;
import org.folio.repository.holdings.HoldingsStatusRepositoryImpl;
import org.folio.repository.holdings.LoadStatus;

@Component
public class LoadServiceFacadeImpl implements LoadServiceFacade {
  private static final int MAX_COUNT = 5000;
  private static final Logger logger = LoggerFactory.getLogger(LoadServiceFacadeImpl.class);
  private final HoldingsService holdingsService;
  private final int loadPageRetries;
  private final int loadPageDelay;
  private long statusRetryDelay;
  private int statusRetryCount;
  private long snapshotRetryDelay;
  private int snapshotRetryCount;
  private Vertx vertx;
  private HoldingsStatusRepository holdingsStatusRepository;

  public LoadServiceFacadeImpl(@Value("${holdings.status.check.delay}") long statusRetryDelay,
                               @Value("${holdings.status.retry.count}") int statusRetryCount,
                               @Value("${holdings.snapshot.retry.delay}") long snapshotRetryDelay,
                               @Value("${holdings.snapshot.retry.count}") int snapshotRetryCount,
                               @Value("${holdings.page.retry.delay}")int loadPageRetryDelay,
                               @Value("${holdings.page.retry.count}")int loadPageRetryCount,
                               Vertx vertx) {
    this.loadPageDelay = loadPageRetryDelay;
    this.loadPageRetries = loadPageRetryCount;
    this.statusRetryDelay = statusRetryDelay;
    this.statusRetryCount = statusRetryCount;
    this.snapshotRetryDelay = snapshotRetryDelay;
    this.snapshotRetryCount = snapshotRetryCount;
    this.vertx = vertx;
    holdingsService = HoldingsService.createProxy(vertx, HoldingConstants.HOLDINGS_SERVICE_ADDRESS);
    this.holdingsStatusRepository = new HoldingsStatusRepositoryImpl(vertx);
  }

  @Override
  public void createSnapshot(ConfigurationMessage configuration) {
    LoadServiceImpl loadingService = new LoadServiceImpl(configuration.getConfiguration(), vertx);
    retryOnFailure(snapshotRetryCount, snapshotRetryDelay, () ->
      populateHoldings(loadingService)
        .thenCompose(isSuccessful -> waitForCompleteStatus(statusRetryCount, loadingService))
        .thenCompose(status -> holdingsStatusRepository.update(getLoadStatusInProgressPopulatedToHoldings(LocalDateTime.now().toString()), configuration.getTenantId()))
        .thenAccept(o -> holdingsService.snapshotCreated(configuration)))
    .exceptionally(throwable -> {
      logger.error("Failed to create snapshot after " + snapshotRetryCount + " retries");
      return null;
    });
  }

  @Override
  public void loadHoldings(ConfigurationMessage configuration) {
    LoadServiceImpl loadingService = new LoadServiceImpl(configuration.getConfiguration(), vertx);

    getLoadingStatus(loadingService)
      .thenAccept(status -> {
        Integer totalCount = status.getTotalCount();
        final String tenantId = configuration.getTenantId();
        holdingsStatusRepository.update(getLoadStatusInProgressLoadingHoldings(LocalDateTime.now().toString(), totalCount), tenantId)
          .thenCompose(o -> loadHoldings(totalCount, tenantId, loadingService))
          .thenAccept(o ->
            holdingsStatusRepository.update(getLoadStatusCompleted(LocalDateTime.now().toString(), totalCount), tenantId));
      });
  }

  private CompletableFuture<Void> populateHoldings(LoadService loadingService) {
    return getLoadingStatus(loadingService).thenCompose(loadStatus -> {
      final LoadStatus other = LoadStatus.fromValue(loadStatus.getStatus());
      if (IN_PROGRESS.equals(other)) {
        return CompletableFuture.completedFuture(null);
      } else {
        logger.info("Start populating holdings to stage environment.");
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
    vertx.setTimer(statusRetryDelay, timerId -> getLoadingStatus(loadingService)
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

  public CompletableFuture<Void> loadHoldings(Integer totalCount, String tenantId, LoadService loadingService) {
    CompletableFuture<Void> future = CompletableFuture.completedFuture(null);
    final int totalRequestCount = getRequestCount(totalCount);

    List<Integer> pagesToLoad = IntStream.range(0, totalRequestCount).boxed().collect(Collectors.toList());
    for (Integer page : pagesToLoad) {
      future = future
        .thenCompose(o -> retryOnFailure(loadPageRetries, loadPageDelay, () -> loadingService.loadHoldings(MAX_COUNT, page)))
        .handle(
          (holdings, e) ->
          {
            if(e == null) {
              holdingsService.saveHolding(new HoldingsMessage(holdings.getHoldingsList()), tenantId);
            }
            else{
              logger.error("Failed to load holdings page " + page + " with count " + MAX_COUNT, e);
            }
            return null;
          });
    }

    return future;
  }

  public <T> CompletableFuture<T> retryOnFailure(int retries, long delay, Producer<CompletableFuture<T>> futureProducer) {
    CompletableFuture<T> future = new CompletableFuture<>();
    retryOnFailure(retries, delay, future, futureProducer);
    return future;
  }

  /**
   * Runs action provided by futureProducer, if future is completed exceptionally then futureProducer will be called again
   * after given delay.
   * @param retries Amount of times action will be retried (e.g. if retries = 2 then futureProducer will be called 2 times)
   * @param delay delay in milliseconds before action is executed again after failure
   * @param future future that will be completed when future from futureProducer completes successfully or amount of retries is exceeded
   * @param futureProducer provides an asynchronous action
   */
  private <T> void retryOnFailure(int retries, long delay, CompletableFuture<T> future, Producer<CompletableFuture<T>> futureProducer) {
    futureProducer.call()
      .thenAccept(future::complete)
      .exceptionally(throwable -> {
        if (retries > 1) {
          vertx.setTimer(delay, timerId -> retryOnFailure(retries - 1, delay, future, futureProducer)
          );
        } else {
          future.completeExceptionally(new IllegalStateException());
        }
        return null;
      });
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
