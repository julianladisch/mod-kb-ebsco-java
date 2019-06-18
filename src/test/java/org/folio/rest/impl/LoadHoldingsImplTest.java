package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

import static org.folio.repository.holdings.HoldingConstants.HOLDINGS_SERVICE_ADDRESS;
import static org.folio.repository.holdings.HoldingConstants.LOAD_FACADE_ADDRESS;
import static org.folio.util.TestUtil.STUB_TENANT;
import static org.folio.util.TestUtil.mockDefaultConfiguration;
import static org.folio.util.TestUtil.mockGet;
import static org.folio.util.TestUtil.readFile;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.SendContext;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.holdingsiq.model.Configuration;
import org.folio.repository.holdings.DbHolding;
import org.folio.service.holdings.ConfigurationMessage;
import org.folio.service.holdings.HoldingsMessage;
import org.folio.service.holdings.LoadServiceFacade;
import org.folio.util.HoldingsTestUtil;

@RunWith(VertxUnitRunner.class)
public class LoadHoldingsImplTest extends WireMockTestBase {

  private static final String SNAPSHOT_CREATED_ACTION = "snapshotCreated";
  private static final String SAVE_HOLDINGS_ACTION = "saveHolding";
  private static final int TIMEOUT = 500;
  private static final int EXPECTED_LOADED_PAGES = 2;
  private static final String STUB_HOLDINGS_TITLE = "java-test-one";
  private static final String HOLDINGS_STATUS_ENDPOINT = "/rm/rmaccounts/" + STUB_CUSTOMER_ID + "/holdings/status";
  private static final String HOLDINGS_POST_HOLDINGS_ENDPOINT = "/rm/rmaccounts/" + STUB_CUSTOMER_ID + "/holdings";
  private static final String HOLDINGS_GET_ENDPOINT = "/rm/rmaccounts/" + STUB_CUSTOMER_ID + "/holdings";
  private static final String LOAD_HOLDINGS_ENDPOINT = "loadHoldings";
  private static final String GET_HOLDINGS_SCENARIO = "Get holdings";
  private static final String COMPLETED_STATE = "Completed state";
  private static final String RETRY_SCENARIO = "Retry scenario";
  private static final String SECOND_TRY = "Second try";
  private static int TEST_SNAPSHOT_RETRY_COUNT = 2;
  private Configuration stubConfiguration;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    stubConfiguration = Configuration.builder()
      .apiKey(STUB_API_KEY)
      .customerId(STUB_CUSTOMER_ID)
      .url(getWiremockUrl())
      .build();
  }

  @Test
  public void shouldSaveHoldings() throws IOException, URISyntaxException {
    mockDefaultConfiguration(getWiremockUrl());

    mockGet(new EqualToPattern(HOLDINGS_STATUS_ENDPOINT), "responses/rmapi/holdings/status/get-status-completed.json");

    stubFor(
      post(new UrlPathPattern(new EqualToPattern(HOLDINGS_POST_HOLDINGS_ENDPOINT), false))
        .willReturn(new ResponseDefinitionBuilder()
          .withBody("")
          .withStatus(202)));

    mockGet(new RegexPattern(HOLDINGS_GET_ENDPOINT), "responses/rmapi/holdings/holdings/get-holdings.json");

    postWithStatus(LOAD_HOLDINGS_ENDPOINT, "", SC_NO_CONTENT);

    final List<DbHolding> holdingsList = HoldingsTestUtil.getHoldings(vertx);
    assertThat(holdingsList.size(), Matchers.notNullValue());
  }

  @Test
  public void shouldRetryCreationOfSnapshotWhenItFails(TestContext context) throws IOException, URISyntaxException {
    mockGet(new EqualToPattern(HOLDINGS_STATUS_ENDPOINT), "responses/rmapi/holdings/status/get-status-completed.json");

    stubFor(
      post(new UrlPathPattern(new EqualToPattern(HOLDINGS_POST_HOLDINGS_ENDPOINT), false))
        .inScenario(RETRY_SCENARIO)
        .willSetStateTo(SECOND_TRY)
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(500)));

    stubFor(
      post(new UrlPathPattern(new EqualToPattern(HOLDINGS_POST_HOLDINGS_ENDPOINT), false))
        .inScenario(RETRY_SCENARIO)
        .whenScenarioStateIs(SECOND_TRY)
        .willReturn(new ResponseDefinitionBuilder()
          .withBody("")
          .withStatus(202)));

    Async async = context.async();
    vertx.eventBus().addInterceptor(
      interceptor(HOLDINGS_SERVICE_ADDRESS, SNAPSHOT_CREATED_ACTION, message -> async.complete()));

    LoadServiceFacade proxy = LoadServiceFacade.createProxy(vertx, LOAD_FACADE_ADDRESS);
    proxy.createSnapshot(new ConfigurationMessage(stubConfiguration, STUB_TENANT));

    async.await(TIMEOUT);
  }

  @Test
  public void shouldStopRetryingAfterMultipleFailures() throws IOException, URISyntaxException, InterruptedException {
    mockGet(new EqualToPattern(HOLDINGS_STATUS_ENDPOINT), "responses/rmapi/holdings/status/get-status-completed.json");

    UrlPathPattern urlPattern = new UrlPathPattern(new EqualToPattern(HOLDINGS_POST_HOLDINGS_ENDPOINT), false);
    stubFor(
      post(urlPattern)
        .willReturn(new ResponseDefinitionBuilder()
          .withStatus(500)));

    LoadServiceFacade proxy = LoadServiceFacade.createProxy(vertx, LOAD_FACADE_ADDRESS);
    proxy.createSnapshot(new ConfigurationMessage(stubConfiguration, STUB_TENANT));

    Thread.sleep(200);
    verify(TEST_SNAPSHOT_RETRY_COUNT,
      RequestPatternBuilder.newRequestPattern(RequestMethod.POST, urlPattern));
  }

  @Test
  public void shouldSendSaveHoldingsEventForEachLoadedPage(TestContext context) throws IOException, URISyntaxException {
    mockGet(new EqualToPattern(HOLDINGS_STATUS_ENDPOINT), "responses/rmapi/holdings/status/get-status-completed.json");
    mockGet(new RegexPattern(HOLDINGS_GET_ENDPOINT), "responses/rmapi/holdings/holdings/get-holdings.json");

    List<HoldingsMessage> messages = new ArrayList<>();
    Async async = context.async(EXPECTED_LOADED_PAGES);
    vertx.eventBus().addInterceptor(
      interceptor(HOLDINGS_SERVICE_ADDRESS, SAVE_HOLDINGS_ACTION,
        message -> {
          messages.add(((JsonObject)message.body()).getJsonObject("holdings").mapTo(HoldingsMessage.class));
          async.countDown();
        }));

    LoadServiceFacade proxy = LoadServiceFacade.createProxy(vertx, LOAD_FACADE_ADDRESS);
    proxy.startLoading(new ConfigurationMessage(stubConfiguration, STUB_TENANT));

    async.await(TIMEOUT);
    assertEquals(2, messages.size());
    assertEquals(STUB_HOLDINGS_TITLE, messages.get(0).getHoldingList().get(0).getPublicationTitle());
  }

  @Ignore("loadHoldings endpoint was changed to return response immediately, " +
    "instead of returning it after loading is complete")
  @Test
  public void shouldWaitForCompleteStatusAndLoadHoldings() throws IOException, URISyntaxException {
    mockDefaultConfiguration(getWiremockUrl());

    stubFor(get(new UrlPathPattern(new RegexPattern(HOLDINGS_STATUS_ENDPOINT), true))
      .inScenario(GET_HOLDINGS_SCENARIO)
      .whenScenarioStateIs(STARTED)
      .willReturn(new ResponseDefinitionBuilder()
        .withBody(readFile("responses/rmapi/holdings/status/get-status-in-progress.json")))
      .willSetStateTo(COMPLETED_STATE));

    stubFor(get(new UrlPathPattern(new RegexPattern(HOLDINGS_STATUS_ENDPOINT), true))
      .inScenario(GET_HOLDINGS_SCENARIO)
      .whenScenarioStateIs(COMPLETED_STATE)
      .willReturn(new ResponseDefinitionBuilder()
        .withBody(readFile("responses/rmapi/holdings/status/get-status-completed.json"))));

    mockGet(new RegexPattern(HOLDINGS_GET_ENDPOINT), "responses/rmapi/holdings/holdings/get-holdings.json");

    postWithStatus(LOAD_HOLDINGS_ENDPOINT, "", SC_NO_CONTENT);

    final List<DbHolding> holdingsList = HoldingsTestUtil.getHoldings(vertx);
    assertThat(holdingsList.size(), equalTo(2));
  }

  private Handler<SendContext> interceptor(String serviceAddress, String serviceMethodName,
                                           Consumer<Message> messageConsumer) {
    return messageContext -> {
      Message message = messageContext.message();
      if (serviceAddress.equals(message.address())
        && serviceMethodName.equals(message.headers().get("action"))) {
        messageConsumer.accept(message);
      } else {
        messageContext.next();
      }
    };
  }
}
