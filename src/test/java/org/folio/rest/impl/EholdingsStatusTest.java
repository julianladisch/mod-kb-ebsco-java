package org.folio.rest.impl;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.matching.RegexPattern;
import com.github.tomakehurst.wiremock.matching.UrlPathPattern;
import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.http.HttpStatus;
import org.folio.rest.jaxrs.model.ConfigurationStatus;
import org.folio.rest.util.RestConstants;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.URISyntaxException;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.folio.util.TestUtil.getRequestSpecificationBuilder;
import static org.folio.util.TestUtil.mockConfiguration;
import static org.folio.util.TestUtil.readFile;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@RunWith(VertxUnitRunner.class)
public class EholdingsStatusTest extends WireMockTestBase {
  @Test
  public void shouldReturnTrueWhenRMAPIRequestCompletesWith200Status() throws IOException, URISyntaxException {

    mockConfiguration(CONFIGURATION_STUB_FILE, getWiremockUrl());

    stubFor(
      get(new UrlPathPattern(new RegexPattern("/rm/rmaccounts.*"), true))
        .willReturn(new ResponseDefinitionBuilder()
          .withBody(readFile("responses/rmapi/vendors/get-zero-vendors-response.json"))));

    RestAssured.given()
      .spec(getRequestSpecification())
      .when()
      .get("eholdings/status")
      .then()
      .statusCode(200)
      .body("data.attributes.isConfigurationValid", equalTo(true));
  }

  @Test
  public void shouldReturnFalseWhenRMAPIRequestCompletesWithErrorStatus() throws IOException, URISyntaxException {
    String wiremockUrl = getWiremockUrl();

    mockConfiguration(CONFIGURATION_STUB_FILE, wiremockUrl);

    stubFor(
      get(new UrlPathPattern(new RegexPattern("/rm/rmaccounts.*"), true))
        .willReturn(new ResponseDefinitionBuilder().withStatus(401)));

    RestAssured.given()
      .spec(getRequestSpecification())
      .when()
      .get("eholdings/status")
      .then()
      .statusCode(200)
      .body("data.attributes.isConfigurationValid", equalTo(false));
  }

  @Test
  public void shouldReturn500OnInvalidOkapiUrl() {
    RequestSpecification spec = getRequestSpecificationBuilder("http://localhost")
      .addHeader(RestConstants.OKAPI_URL_HEADER, "wrongUrl^").build();
    RestAssured.given()
      .spec(spec).port(port)
      .when()
      .get("eholdings/status")
      .then()
      .statusCode(500);
  }

  @Test
  public void shouldReturnFalseIfEmptyConfig() throws IOException, URISyntaxException {
    mockConfiguration("responses/configuration/get-configuration-empty.json", null);

    ConfigurationStatus status = RestAssured.given()
      .spec(getRequestSpecification())
      .when()
      .get("eholdings/status")
      .then()
      .statusCode(HttpStatus.SC_OK).extract().as(ConfigurationStatus.class);

    assertThat(status.getData().getAttributes().getIsConfigurationValid(), equalTo(false));
  }
}
