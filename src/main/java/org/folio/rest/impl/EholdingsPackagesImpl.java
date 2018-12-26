package org.folio.rest.impl;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.folio.http.HttpConsts.CONTENT_TYPE_HEADER;
import static org.folio.http.HttpConsts.JSON_API_TYPE;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.http.HttpStatus;
import org.folio.config.api.RMAPIConfigurationService;
import org.folio.rest.annotations.Validate;
import org.folio.rest.aspect.HandleValidationErrors;
import org.folio.rest.converter.PackagesConverter;
import org.folio.rest.converter.ResourcesConverter;
import org.folio.rest.exception.InputValidationException;
import org.folio.rest.jaxrs.model.PackageCollection;
import org.folio.rest.jaxrs.model.PackagePostRequest;
import org.folio.rest.jaxrs.model.PackagePutRequest;
import org.folio.rest.jaxrs.resource.EholdingsPackages;
import org.folio.rest.model.FilterQuery;
import org.folio.rest.model.OkapiData;
import org.folio.rest.model.PackageId;
import org.folio.rest.model.Sort;
import org.folio.rest.parser.IdParser;
import org.folio.rest.util.ErrorHandler;
import org.folio.rest.util.ErrorUtil;
import org.folio.rest.util.template.RMAPITemplateFactory;
import org.folio.rest.validator.CustomPackagePutBodyValidator;
import org.folio.rest.validator.HeaderValidator;
import org.folio.rest.validator.PackageParametersValidator;
import org.folio.rest.validator.PackagePutBodyValidator;
import org.folio.rest.validator.PackagesPostBodyValidator;
import org.folio.rest.validator.TitleParametersValidator;
import org.folio.rmapi.RMAPIService;
import org.folio.rmapi.exception.RMAPIResourceNotFoundException;
import org.folio.rmapi.exception.RMAPIServiceException;
import org.folio.rmapi.exception.RMAPIUnAuthorizedException;
import org.folio.rmapi.model.PackagePost;
import org.folio.rmapi.model.PackagePut;
import org.folio.spring.SpringContextUtil;
import org.springframework.beans.factory.annotation.Autowired;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class EholdingsPackagesImpl implements EholdingsPackages {

  private static final String PUT_PACKAGE_ERROR_MESSAGE = "Failed to update package";
  private static final String POST_PACKAGES_ERROR_MESSAGE = "Failed to create packages";
  private static final String GET_PACKAGE_RESOURCES_ERROR_MESSAGE = "Failed to retrieve package resources";
  private static final String PACKAGE_NOT_FOUND_MESSAGE = "Package not found";

  private static final String INVALID_PACKAGE_TITLE = "Package cannot be deleted";
  private static final String INVALID_PACKAGE_DETAILS = "Invalid package";

  private final Logger logger = LoggerFactory.getLogger(EholdingsPackagesImpl.class);


  @Autowired
  private RMAPIConfigurationService configurationService;
  @Autowired
  private PackagesConverter converter;
  @Autowired
  private HeaderValidator headerValidator;
  @Autowired
  private PackageParametersValidator packageParametersValidator;
  @Autowired
  private PackagePutBodyValidator packagePutBodyValidator;
  @Autowired
  private CustomPackagePutBodyValidator customPackagePutBodyValidator;
  @Autowired
  private PackagesPostBodyValidator packagesPostBodyValidator;
  @Autowired
  private TitleParametersValidator titleParametersValidator;
  @Autowired
  private ResourcesConverter resourceConverter;
  @Autowired
  private IdParser idParser;
  @Autowired
  private RMAPITemplateFactory templateFactory;

  public EholdingsPackagesImpl() {
    SpringContextUtil.autowireDependencies(this, Vertx.currentContext());
  }

  @Override
  @Validate
  @HandleValidationErrors
  public void getEholdingsPackages(String filterCustom, String q, String filterSelected,
                                   String filterType, String sort, int page, int count, Map<String, String> okapiHeaders,
                                   Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    headerValidator.validate(okapiHeaders);
    packageParametersValidator.validate(filterCustom, filterSelected, filterType, sort, q);

    boolean isFilterCustom = Boolean.parseBoolean(filterCustom);
    Sort nameSort = Sort.valueOf(sort.toUpperCase());

    templateFactory.createTemplate(okapiHeaders, asyncResultHandler)
      .requestAction((rmapiService, okapiData) ->
        rmapiService.getVendors(isFilterCustom)
        .thenCompose(vendors ->
          rmapiService.retrievePackages(filterSelected, filterType, rmapiService.getFirstProviderElement(vendors),
            q, page, count, nameSort)))
      .addErrorMapper(RMAPIServiceException.class,
        exception ->
          GetEholdingsPackagesResponse.respond400WithApplicationVndApiJson(
            ErrorUtil.createErrorFromRMAPIResponse(exception)))
      .executeWithResult(PackageCollection.class);
  }

  @Override
  @HandleValidationErrors
  public void postEholdingsPackages(String contentType, PackagePostRequest entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    headerValidator.validate(okapiHeaders);
    packagesPostBodyValidator.validate(entity);

    PackagePost packagePost = converter.convertToPackage(entity);

    MutableObject<RMAPIService> service = new MutableObject<>();
    CompletableFuture.completedFuture(null)
      .thenCompose(o -> configurationService.retrieveConfiguration(new OkapiData(okapiHeaders)))
      .thenAccept(rmapiConfiguration ->
        service.setValue(new RMAPIService(rmapiConfiguration.getCustomerId(),
          rmapiConfiguration.getAPIKey(), rmapiConfiguration.getUrl(), vertxContext.owner())))
      .thenCompose(o -> service.getValue().postPackage(packagePost))
      .thenAccept(packageCreated ->
        asyncResultHandler.handle(Future.succeededFuture(PostEholdingsPackagesResponse
          .respond200WithApplicationVndApiJson(converter.convert(packageCreated)))))
      .exceptionally(e -> {
        logger.error(POST_PACKAGES_ERROR_MESSAGE, e);
        new ErrorHandler()
          .add(RMAPIServiceException.class,
            exception ->
              PostEholdingsPackagesResponse.respond400WithApplicationVndApiJson(
                ErrorUtil.createErrorFromRMAPIResponse(exception)))
          .addDefaultMapper()
          .handle(asyncResultHandler, e);
        return null;
      });

  }

  @Override
  @HandleValidationErrors
  public void getEholdingsPackagesByPackageId(String packageId, String include, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PackageId parsedPackageId = idParser.parsePackageId(packageId);
    headerValidator.validate(okapiHeaders);
    List<String> includedObjects = include != null ? Arrays.asList(include.split(",")) : Collections.emptyList();

    CompletableFuture.completedFuture(null)
      .thenCompose(okapiData -> configurationService.retrieveConfiguration(new OkapiData(okapiHeaders)))
      .thenCompose(rmapiConfiguration -> {
        RMAPIService rmapiService = new RMAPIService(rmapiConfiguration.getCustomerId(), rmapiConfiguration.getAPIKey(),
          rmapiConfiguration.getUrl(), vertxContext.owner());
        return rmapiService.retrievePackage(parsedPackageId, includedObjects);
      })
      .thenAccept(result ->
        asyncResultHandler.handle(Future.succeededFuture(
          GetEholdingsPackagesByPackageIdResponse.respond200WithApplicationVndApiJson(
            converter.convert(result.getPackageData(), result.getVendor(), result.getTitles())))))
      .exceptionally(e -> {
        logger.error(INTERNAL_SERVER_ERROR, e);
        handleError(asyncResultHandler, e);
        return null;
      });
  }

  @Override
  @HandleValidationErrors
  public void putEholdingsPackagesByPackageId(String packageId, String contentType, PackagePutRequest entity, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    headerValidator.validate(okapiHeaders);
    PackageId parsedPackageId = idParser.parsePackageId(packageId);
    MutableObject<RMAPIService> rmapiService = new MutableObject<>();
    CompletableFuture.completedFuture(null)
      .thenCompose(o -> configurationService.retrieveConfiguration(new OkapiData(okapiHeaders)))
      .thenCompose(rmapiConfiguration -> {
        rmapiService.setValue(new RMAPIService(rmapiConfiguration.getCustomerId(),
          rmapiConfiguration.getAPIKey(), rmapiConfiguration.getUrl(), vertxContext.owner()));
        return rmapiService.getValue().retrievePackage(parsedPackageId);
      })
      .thenCompose(packageData -> {
        PackagePut packagePutBody;
        if (packageData.getIsCustom()) {
          customPackagePutBodyValidator.validate(entity);
          packagePutBody = converter.convertToRMAPICustomPackagePutRequest(entity);
        } else {
          packagePutBodyValidator.validate(entity);
          packagePutBody = converter.convertToRMAPIPackagePutRequest(entity);
        }
        return rmapiService.getValue().updatePackage(parsedPackageId, packagePutBody);
      })
      .thenCompose(o -> rmapiService.getValue().retrievePackage(parsedPackageId))
      .thenAccept(packageData ->
        asyncResultHandler.handle(Future.succeededFuture(EholdingsPackages.PutEholdingsPackagesByPackageIdResponse
          .respond200WithApplicationVndApiJson(converter.convert(packageData)))))
      .exceptionally(e -> {
        logger.error(PUT_PACKAGE_ERROR_MESSAGE, e);
        new ErrorHandler()
          .add(InputValidationException.class, exception ->
            EholdingsPackages.PutEholdingsPackagesByPackageIdResponse.respond422WithApplicationVndApiJson(
              ErrorUtil.createError(exception.getMessage(), exception.getMessageDetail())))
          .addRmApiMapper()
          .addDefaultMapper()
          .handle(asyncResultHandler, e);
        return null;
      });

  }

  @Override
  @HandleValidationErrors
  public void deleteEholdingsPackagesByPackageId(String packageId, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    headerValidator.validate(okapiHeaders);
    PackageId parsedPackageId = idParser.parsePackageId(packageId);
    templateFactory.createTemplate(okapiHeaders, asyncResultHandler)
      .requestAction((rmapiService, okapiData) ->
        rmapiService.retrievePackage(parsedPackageId)
          .thenCompose(packageData -> {
            if (!packageData.getIsCustom()) {
              throw new InputValidationException(INVALID_PACKAGE_TITLE, INVALID_PACKAGE_DETAILS);
            }
            return rmapiService.deletePackage(parsedPackageId);
          }))
      .execute();
  }

  @Override
  @Validate
  @HandleValidationErrors
  public void getEholdingsPackagesResourcesByPackageId(String packageId, String sort, String filterSelected, String filterType, String filterName, String filterIsxn, String filterSubject, String filterPublisher,  int page,   int count, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    PackageId parsedPackageId = idParser.parsePackageId(packageId);
    headerValidator.validate(okapiHeaders);

    FilterQuery fq = FilterQuery.builder()
        .selected(filterSelected).type(filterType)
        .name(filterName).isxn(filterIsxn).subject(filterSubject)
        .publisher(filterPublisher).build();

    titleParametersValidator.validate(fq, sort, true);

    Sort nameSort = Sort.valueOf(sort.toUpperCase());

    CompletableFuture.completedFuture(null)
      .thenCompose(okapiData -> configurationService.retrieveConfiguration(new OkapiData(okapiHeaders)))
      .thenCompose(rmapiConfiguration -> {
        RMAPIService rmapiService = new RMAPIService(rmapiConfiguration.getCustomerId(), rmapiConfiguration.getAPIKey(),
          rmapiConfiguration.getUrl(), vertxContext.owner());
        return rmapiService.retrieveTitles(parsedPackageId.getProviderIdPart(),parsedPackageId.getPackageIdPart(), fq, nameSort, page, count);
      })
      .thenAccept(resourceList ->
        asyncResultHandler.handle(Future.succeededFuture(
            GetEholdingsPackagesResourcesByPackageIdResponse.respond200WithApplicationVndApiJson(resourceConverter.convertFromRMAPIResourceList(resourceList)))))
      .exceptionally(e -> {
        logger.error(GET_PACKAGE_RESOURCES_ERROR_MESSAGE, e);
        new ErrorHandler()
          .add(RMAPIResourceNotFoundException.class, exception ->
            GetEholdingsPackagesResourcesByPackageIdResponse.respond404WithApplicationVndApiJson(
              ErrorUtil.createError(PACKAGE_NOT_FOUND_MESSAGE)))
          .add(RMAPIUnAuthorizedException.class, rmApiException ->
            GetEholdingsPackagesResourcesByPackageIdResponse
              .status(HttpStatus.SC_FORBIDDEN)
              .header(CONTENT_TYPE_HEADER, JSON_API_TYPE)
              .entity(ErrorUtil.createError(rmApiException.getMessage()))
              .build())
          .addRmApiMapper()
          .addDefaultMapper()
          .handle(asyncResultHandler, e);
        return null;
      });
  }

  private void handleError(Handler<AsyncResult<Response>> asyncResultHandler, Throwable e) {
    new ErrorHandler()
      .addRmApiMapper()
      .addInputValidationMapper()
      .addDefaultMapper()
      .handle(asyncResultHandler, e);
  }

}
