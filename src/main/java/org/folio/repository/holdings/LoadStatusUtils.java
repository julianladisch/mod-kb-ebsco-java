package org.folio.repository.holdings;

import java.util.List;

import org.folio.rest.jaxrs.model.HoldingsLoadingStatus;
import org.folio.rest.jaxrs.model.JsonapiErrorResponse;
import org.folio.rest.jaxrs.model.LoadDetailsEnum;
import org.folio.rest.jaxrs.model.LoadStatusAttributes;
import org.folio.rest.jaxrs.model.LoadStatusDetails;
import org.folio.rest.jaxrs.model.LoadStatusEnum;
import org.folio.rest.util.RestConstants;

public class LoadStatusUtils {

  public static HoldingsLoadingStatus getLoadStatusNotStarted() {
    return new HoldingsLoadingStatus()
      .withStatus(new LoadStatusDetails().withName(LoadStatusEnum.NOT_STARTED))
      .withJsonapi(RestConstants.JSONAPI);
  }

  public static HoldingsLoadingStatus getLoadStatusStarted(String date) {
    return new HoldingsLoadingStatus()
      .withStatus(new LoadStatusDetails().withName(LoadStatusEnum.STARTED))
      .withAttributes(new LoadStatusAttributes().withStarted(date))
      .withJsonapi(RestConstants.JSONAPI);
  }

  public static HoldingsLoadingStatus getLoadStatusInProgressPopulatedToHoldings(String date) {
    return new HoldingsLoadingStatus()
      .withStatus(new LoadStatusDetails()
        .withName(LoadStatusEnum.IN_PROGRESS)
        .withDetail(LoadDetailsEnum.POPULATED_TO_HOLDINGS))
      .withAttributes(new LoadStatusAttributes().withStarted(date).withTotalCount(0))
      .withJsonapi(RestConstants.JSONAPI);
  }

  public static HoldingsLoadingStatus getLoadStatusInProgressLoadingHoldings(String date, int totalCount) {
    return new HoldingsLoadingStatus()
      .withStatus(new LoadStatusDetails()
        .withName(LoadStatusEnum.IN_PROGRESS)
        .withDetail(LoadDetailsEnum.LOADING_HOLDINGS))
      .withAttributes(new LoadStatusAttributes().withStarted(date).withTotalCount(totalCount))
      .withJsonapi(RestConstants.JSONAPI);
  }

  public static HoldingsLoadingStatus getLoadStatusInProgressSavingHoldings() {
    return new HoldingsLoadingStatus()
      .withStatus(new LoadStatusDetails()
        .withName(LoadStatusEnum.IN_PROGRESS)
        .withDetail(LoadDetailsEnum.SAVING_HOLDINGS))
      .withJsonapi(RestConstants.JSONAPI);
  }

  public static HoldingsLoadingStatus getLoadStatusCompleted(String finishedDate, int totalCount) {
    return new HoldingsLoadingStatus()
      .withStatus(new LoadStatusDetails()
        .withName(LoadStatusEnum.COMPLETED))
      .withAttributes(new LoadStatusAttributes()
        .withFinished(finishedDate)
        .withTotalCount(totalCount))
      .withJsonapi(RestConstants.JSONAPI);
  }

  public static HoldingsLoadingStatus getLoadStatusFailed(List<JsonapiErrorResponse> errors) {
    return new HoldingsLoadingStatus()
      .withStatus(new LoadStatusDetails()
        .withName(LoadStatusEnum.FAILED))
      .withAttributes(new LoadStatusAttributes()
        .withErrors(errors))
      .withJsonapi(RestConstants.JSONAPI);
  }
}
