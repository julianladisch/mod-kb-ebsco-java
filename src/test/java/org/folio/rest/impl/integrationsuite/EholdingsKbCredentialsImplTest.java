package org.folio.rest.impl.integrationsuite;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNPROCESSABLE_ENTITY;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import static org.folio.repository.assigneduser.AssignedUsersConstants.ASSIGNED_USERS_TABLE_NAME;
import static org.folio.repository.kbcredentials.KbCredentialsTableConstants.KB_CREDENTIALS_TABLE_NAME;
import static org.folio.util.AssignedUsersTestUtil.insertAssignedUser;
import static org.folio.util.KBTestUtil.clearDataFromTable;
import static org.folio.util.KbCredentialsTestUtil.KB_CREDENTIALS_ENDPOINT;
import static org.folio.util.KbCredentialsTestUtil.STUB_API_URL;
import static org.folio.util.KbCredentialsTestUtil.STUB_CREDENTIALS_NAME;
import static org.folio.util.KbCredentialsTestUtil.STUB_TOKEN_HEADER;
import static org.folio.util.KbCredentialsTestUtil.STUB_USERNAME;
import static org.folio.util.KbCredentialsTestUtil.STUB_USER_ID;
import static org.folio.util.KbCredentialsTestUtil.getKbCredentials;
import static org.folio.util.KbCredentialsTestUtil.insertKbCredentials;

import java.util.List;
import java.util.UUID;

import io.vertx.core.json.Json;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import joptsimple.internal.Strings;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.folio.rest.impl.WireMockTestBase;
import org.folio.rest.jaxrs.model.JsonapiError;
import org.folio.rest.jaxrs.model.KbCredentials;
import org.folio.rest.jaxrs.model.KbCredentialsCollection;
import org.folio.rest.jaxrs.model.KbCredentialsDataAttributes;
import org.folio.rest.jaxrs.model.KbCredentialsPostRequest;
import org.folio.rest.jaxrs.model.KbCredentialsPutRequest;

@RunWith(VertxUnitRunner.class)
public class EholdingsKbCredentialsImplTest extends WireMockTestBase {

  @After
  public void tearDown() {
    clearDataFromTable(vertx, ASSIGNED_USERS_TABLE_NAME);
    clearDataFromTable(vertx, KB_CREDENTIALS_TABLE_NAME);
  }

  @Test
  public void shouldReturnKbCredentialsCollectionOnGet() {
    insertKbCredentials(STUB_API_URL, STUB_CREDENTIALS_NAME, STUB_API_KEY, STUB_CUSTOMER_ID, vertx);
    KbCredentialsCollection actual = getWithOk(KB_CREDENTIALS_ENDPOINT).as(KbCredentialsCollection.class);

    assertEquals(1, actual.getData().size());
    assertEquals(Integer.valueOf(1), actual.getMeta().getTotalResults());
    assertNotNull(actual.getData().get(0));
    assertNotNull(actual.getData().get(0).getId());

    assertEquals(STUB_API_URL, actual.getData().get(0).getAttributes().getUrl());
    assertEquals(STUB_CREDENTIALS_NAME, actual.getData().get(0).getAttributes().getName());
    assertEquals(STUB_CUSTOMER_ID, actual.getData().get(0).getAttributes().getCustomerId());
    assertEquals(StringUtils.repeat("*", 40), actual.getData().get(0).getAttributes().getApiKey());

    assertEquals(STUB_USERNAME, actual.getData().get(0).getMeta().getCreatedByUsername());
    assertEquals(STUB_USER_ID, actual.getData().get(0).getMeta().getCreatedByUserId());
    assertNotNull(actual.getData().get(0).getMeta().getCreatedDate());
  }

  @Test
  public void shouldReturnEmptyKbCredentialsCollectionOnGet() {
    KbCredentialsCollection actual = getWithOk(KB_CREDENTIALS_ENDPOINT).as(KbCredentialsCollection.class);

    assertNotNull(actual.getData());
    assertEquals(0, actual.getData().size());
    assertEquals(Integer.valueOf(0), actual.getMeta().getTotalResults());
  }

  @Test
  public void shouldReturn201OnPostIfCredentialsAreValid() {
    KbCredentialsPostRequest kbCredentialsPostRequest = new KbCredentialsPostRequest()
      .withData(new KbCredentials()
        .withType(KbCredentials.Type.KB_CREDENTIALS)
        .withAttributes(new KbCredentialsDataAttributes()
          .withName(STUB_CREDENTIALS_NAME)
          .withCustomerId(STUB_CUSTOMER_ID)
          .withApiKey(STUB_API_KEY)
          .withUrl(getWiremockUrl())));
    String postBody = Json.encode(kbCredentialsPostRequest);

    stubForSuccessCredentials();
    KbCredentials actual = postWithStatus(KB_CREDENTIALS_ENDPOINT, postBody, SC_CREATED, STUB_TOKEN_HEADER)
      .as(KbCredentials.class);

    assertNotNull(actual);
    assertNotNull(actual.getId());
    assertNotNull(actual.getType());
    assertEquals(getWiremockUrl(), actual.getAttributes().getUrl());
    assertEquals(STUB_CREDENTIALS_NAME, actual.getAttributes().getName());
    assertEquals(STUB_CUSTOMER_ID, actual.getAttributes().getCustomerId());
    assertEquals(STUB_USERNAME, actual.getMeta().getCreatedByUsername());
    assertEquals(STUB_USER_ID, actual.getMeta().getCreatedByUserId());
    assertNotNull(actual.getMeta().getCreatedDate());
  }

  @Test
  public void shouldReturn422OnPostWhenCredentialsAreInvalid() {
    KbCredentialsPostRequest kbCredentialsPostRequest = new KbCredentialsPostRequest()
      .withData(new KbCredentials()
        .withType(KbCredentials.Type.KB_CREDENTIALS)
        .withAttributes(new KbCredentialsDataAttributes()
          .withName(STUB_CREDENTIALS_NAME)
          .withCustomerId(STUB_CUSTOMER_ID)
          .withApiKey(STUB_API_KEY)
          .withUrl(getWiremockUrl())));
    String postBody = Json.encode(kbCredentialsPostRequest);

    stubForFailedCredentials();
    JsonapiError error = postWithStatus(KB_CREDENTIALS_ENDPOINT, postBody, SC_UNPROCESSABLE_ENTITY, STUB_TOKEN_HEADER)
      .as(JsonapiError.class);

    assertEquals("KB API Credentials are invalid", error.getErrors().get(0).getTitle());
  }

  @Test
  public void shouldReturn422OnPostWhenCredentialsNameIsLongerThen255() {
    KbCredentialsPostRequest kbCredentialsPostRequest = new KbCredentialsPostRequest()
      .withData(new KbCredentials()
        .withType(KbCredentials.Type.KB_CREDENTIALS)
        .withAttributes(new KbCredentialsDataAttributes()
          .withName(Strings.repeat('*', 256))
          .withCustomerId(STUB_CUSTOMER_ID)
          .withApiKey(STUB_API_KEY)
          .withUrl(getWiremockUrl())));
    String postBody = Json.encode(kbCredentialsPostRequest);

    JsonapiError error = postWithStatus(KB_CREDENTIALS_ENDPOINT, postBody, SC_UNPROCESSABLE_ENTITY, STUB_TOKEN_HEADER)
      .as(JsonapiError.class);

    assertEquals("Invalid name", error.getErrors().get(0).getTitle());
    assertEquals("name is too long (maximum is 255 characters)", error.getErrors().get(0).getDetail());
  }

  @Test
  public void shouldReturn422OnPostWhenCredentialsNameIsEmpty() {
    KbCredentialsPostRequest kbCredentialsPostRequest = new KbCredentialsPostRequest()
      .withData(new KbCredentials()
        .withType(KbCredentials.Type.KB_CREDENTIALS)
        .withAttributes(new KbCredentialsDataAttributes()
          .withName("")
          .withCustomerId(STUB_CUSTOMER_ID)
          .withApiKey(STUB_API_KEY)
          .withUrl(getWiremockUrl())));
    String postBody = Json.encode(kbCredentialsPostRequest);

    JsonapiError error = postWithStatus(KB_CREDENTIALS_ENDPOINT, postBody, SC_UNPROCESSABLE_ENTITY, STUB_TOKEN_HEADER)
      .as(JsonapiError.class);

    assertEquals("Invalid name", error.getErrors().get(0).getTitle());
    assertEquals("name must not be empty", error.getErrors().get(0).getDetail());
  }

  @Test
  public void shouldReturn422OnPostWhenCredentialsWithProvidedNameAlreadyExist() {
    insertKbCredentials(STUB_API_URL, STUB_CREDENTIALS_NAME, STUB_API_KEY, STUB_CUSTOMER_ID, vertx);
    KbCredentialsPostRequest kbCredentialsPostRequest = new KbCredentialsPostRequest()
      .withData(new KbCredentials()
        .withType(KbCredentials.Type.KB_CREDENTIALS)
        .withAttributes(new KbCredentialsDataAttributes()
          .withName(STUB_CREDENTIALS_NAME)
          .withCustomerId(STUB_CUSTOMER_ID)
          .withApiKey(STUB_API_KEY)
          .withUrl(getWiremockUrl())));
    String postBody = Json.encode(kbCredentialsPostRequest);

    stubForSuccessCredentials();
    JsonapiError error = postWithStatus(KB_CREDENTIALS_ENDPOINT, postBody, SC_UNPROCESSABLE_ENTITY, STUB_TOKEN_HEADER)
      .as(JsonapiError.class);

    assertEquals("Duplicate name", error.getErrors().get(0).getTitle());
    assertEquals(String.format("Credentials with name '%s' already exist", STUB_CREDENTIALS_NAME),
      error.getErrors().get(0).getDetail());
  }

  @Test
  public void shouldReturnKbCredentialsOnGet() {
    String credentialsId = insertKbCredentials(STUB_API_URL, STUB_CREDENTIALS_NAME, STUB_API_KEY, STUB_CUSTOMER_ID, vertx);

    String resourcePath = KB_CREDENTIALS_ENDPOINT + "/" + credentialsId;
    KbCredentials actual = getWithOk(resourcePath).as(KbCredentials.class);

    assertEquals(getKbCredentials(vertx).get(0), actual);
  }

  @Test
  public void shouldReturn400OnGetWhenIdIsInvalid() {
    String resourcePath = KB_CREDENTIALS_ENDPOINT + "/invalid-id";
    JsonapiError error = getWithStatus(resourcePath, SC_BAD_REQUEST).as(JsonapiError.class);

    assertThat(error.getErrors().get(0).getTitle(), containsString("'id' parameter is incorrect."));
  }

  @Test
  public void shouldReturn404OnGetWhenCredentialsAreMissing() {
    String resourcePath = KB_CREDENTIALS_ENDPOINT + "/11111111-1111-1111-a111-111111111111";
    JsonapiError error = getWithStatus(resourcePath, SC_NOT_FOUND).as(JsonapiError.class);

    assertThat(error.getErrors().get(0).getTitle(), containsString("KbCredentials not found by id"));
  }

  @Test
  public void shouldReturn204OnPutIfCredentialsAreValid() {
    String credentialsId = insertKbCredentials(STUB_API_URL, STUB_CREDENTIALS_NAME, STUB_API_KEY, STUB_CUSTOMER_ID, vertx);

    KbCredentialsPutRequest kbCredentialsPutRequest = new KbCredentialsPutRequest()
      .withData(new KbCredentials()
        .withType(KbCredentials.Type.KB_CREDENTIALS)
        .withAttributes(new KbCredentialsDataAttributes()
          .withName(STUB_CREDENTIALS_NAME + "updated")
          .withCustomerId(STUB_CUSTOMER_ID + "updated")
          .withApiKey(STUB_API_KEY)
          .withUrl(getWiremockUrl())));
    String putBody = Json.encode(kbCredentialsPutRequest);

    stubForSuccessCredentials();
    String resourcePath = KB_CREDENTIALS_ENDPOINT + "/" + credentialsId;
    putWithNoContent(resourcePath, putBody, STUB_TOKEN_HEADER);

    KbCredentials actual = getKbCredentials(vertx).get(0);

    assertNotNull(actual);
    assertNotNull(actual.getId());
    assertNotNull(actual.getType());
    assertEquals(getWiremockUrl(), actual.getAttributes().getUrl());
    assertEquals(STUB_CREDENTIALS_NAME + "updated", actual.getAttributes().getName());
    assertEquals(STUB_CUSTOMER_ID + "updated", actual.getAttributes().getCustomerId());
    assertEquals(STUB_USERNAME, actual.getMeta().getCreatedByUsername());
    assertEquals(STUB_USER_ID, actual.getMeta().getCreatedByUserId());
    assertNotNull(actual.getMeta().getCreatedDate());
    assertEquals(STUB_USERNAME, actual.getMeta().getUpdatedByUsername());
    assertEquals(STUB_USER_ID, actual.getMeta().getUpdatedByUserId());
    assertNotNull(actual.getMeta().getUpdatedDate());
  }

  @Test
  public void shouldReturn422OnPutWhenCredentialsAreInvalid() {
    String credentialsId = insertKbCredentials(STUB_API_URL, STUB_CREDENTIALS_NAME, STUB_API_KEY, STUB_CUSTOMER_ID, vertx);

    KbCredentialsPutRequest kbCredentialsPutRequest = new KbCredentialsPutRequest()
      .withData(new KbCredentials()
        .withType(KbCredentials.Type.KB_CREDENTIALS)
        .withAttributes(new KbCredentialsDataAttributes()
          .withName(STUB_CREDENTIALS_NAME)
          .withCustomerId(STUB_CUSTOMER_ID)
          .withApiKey(STUB_API_KEY)
          .withUrl(getWiremockUrl())));
    String putBody = Json.encode(kbCredentialsPutRequest);

    stubForFailedCredentials();
    String resourcePath = KB_CREDENTIALS_ENDPOINT + "/" + credentialsId;
    JsonapiError error = putWithStatus(resourcePath, putBody, SC_UNPROCESSABLE_ENTITY, STUB_TOKEN_HEADER)
      .as(JsonapiError.class);

    assertEquals("KB API Credentials are invalid", error.getErrors().get(0).getTitle());
  }

  @Test
  public void shouldReturn422OnPutWhenCredentialsNameIsLongerThen255() {
    KbCredentialsPutRequest kbCredentialsPutRequest = new KbCredentialsPutRequest()
      .withData(new KbCredentials()
        .withType(KbCredentials.Type.KB_CREDENTIALS)
        .withAttributes(new KbCredentialsDataAttributes()
          .withName(Strings.repeat('*', 256))
          .withCustomerId(STUB_CUSTOMER_ID)
          .withApiKey(STUB_API_KEY)
          .withUrl(getWiremockUrl())));
    String putBody = Json.encode(kbCredentialsPutRequest);

    String resourcePath = KB_CREDENTIALS_ENDPOINT + "/11111111-1111-1111-a111-111111111111";
    JsonapiError error = putWithStatus(resourcePath, putBody, SC_UNPROCESSABLE_ENTITY, STUB_TOKEN_HEADER)
      .as(JsonapiError.class);

    assertEquals("Invalid name", error.getErrors().get(0).getTitle());
    assertEquals("name is too long (maximum is 255 characters)", error.getErrors().get(0).getDetail());
  }

  @Test
  public void shouldReturn422OnPutWhenCredentialsNameIsEmpty() {
    KbCredentialsPutRequest kbCredentialsPutRequest = new KbCredentialsPutRequest()
      .withData(new KbCredentials()
        .withType(KbCredentials.Type.KB_CREDENTIALS)
        .withAttributes(new KbCredentialsDataAttributes()
          .withName("")
          .withCustomerId(STUB_CUSTOMER_ID)
          .withApiKey(STUB_API_KEY)
          .withUrl(getWiremockUrl())));
    String putBody = Json.encode(kbCredentialsPutRequest);

    String resourcePath = KB_CREDENTIALS_ENDPOINT + "/11111111-1111-1111-a111-111111111111";
    JsonapiError error = putWithStatus(resourcePath, putBody, SC_UNPROCESSABLE_ENTITY, STUB_TOKEN_HEADER)
      .as(JsonapiError.class);

    assertEquals("Invalid name", error.getErrors().get(0).getTitle());
    assertEquals("name must not be empty", error.getErrors().get(0).getDetail());
  }

  @Test
  public void shouldReturn422OnPutWhenCredentialsWithProvidedNameAlreadyExist() {
    insertKbCredentials(STUB_API_URL, STUB_CREDENTIALS_NAME, STUB_API_KEY, STUB_CUSTOMER_ID, vertx);
    String credentialsId =
      insertKbCredentials(STUB_API_URL, STUB_CREDENTIALS_NAME + "2", STUB_API_KEY, STUB_CUSTOMER_ID, vertx);

    KbCredentialsPutRequest kbCredentialsPutRequest = new KbCredentialsPutRequest()
      .withData(new KbCredentials()
        .withType(KbCredentials.Type.KB_CREDENTIALS)
        .withAttributes(new KbCredentialsDataAttributes()
          .withName(STUB_CREDENTIALS_NAME)
          .withCustomerId(STUB_CUSTOMER_ID)
          .withApiKey(STUB_API_KEY)
          .withUrl(getWiremockUrl())));
    String putBody = Json.encode(kbCredentialsPutRequest);

    stubForSuccessCredentials();
    String resourcePath = KB_CREDENTIALS_ENDPOINT + "/" + credentialsId;
    JsonapiError error = putWithStatus(resourcePath, putBody, SC_UNPROCESSABLE_ENTITY, STUB_TOKEN_HEADER)
      .as(JsonapiError.class);

    assertEquals("Duplicate name", error.getErrors().get(0).getTitle());
    assertEquals(String.format("Credentials with name '%s' already exist", STUB_CREDENTIALS_NAME),
      error.getErrors().get(0).getDetail());
  }

  @Test
  public void shouldReturn400OnPutWhenIdIsInvalid() {
    KbCredentialsPutRequest kbCredentialsPutRequest = new KbCredentialsPutRequest()
      .withData(new KbCredentials()
        .withId(UUID.randomUUID().toString())
        .withType(KbCredentials.Type.KB_CREDENTIALS)
        .withAttributes(new KbCredentialsDataAttributes()
          .withName(STUB_CREDENTIALS_NAME)
          .withCustomerId(STUB_CUSTOMER_ID)
          .withApiKey(STUB_API_KEY)
          .withUrl(getWiremockUrl())));
    String putBody = Json.encode(kbCredentialsPutRequest);

    String resourcePath = KB_CREDENTIALS_ENDPOINT + "/invalid-id";
    JsonapiError error = putWithStatus(resourcePath, putBody, SC_BAD_REQUEST).as(JsonapiError.class);

    assertThat(error.getErrors().get(0).getTitle(), containsString("'id' parameter is incorrect."));
  }

  @Test
  public void shouldReturn404OnPutWhenCredentialsAreMissing() {
    KbCredentialsPutRequest kbCredentialsPutRequest = new KbCredentialsPutRequest()
      .withData(new KbCredentials()
        .withId(UUID.randomUUID().toString())
        .withType(KbCredentials.Type.KB_CREDENTIALS)
        .withAttributes(new KbCredentialsDataAttributes()
          .withName(STUB_CREDENTIALS_NAME)
          .withCustomerId(STUB_CUSTOMER_ID)
          .withApiKey(STUB_API_KEY)
          .withUrl(getWiremockUrl())));
    String putBody = Json.encode(kbCredentialsPutRequest);

    stubForSuccessCredentials();
    String resourcePath = KB_CREDENTIALS_ENDPOINT + "/11111111-1111-1111-a111-111111111111";
    JsonapiError error = putWithStatus(resourcePath, putBody, SC_NOT_FOUND, STUB_TOKEN_HEADER).as(JsonapiError.class);

    assertThat(error.getErrors().get(0).getTitle(), containsString("KbCredentials not found by id"));
  }

  @Test
  public void shouldReturn201OnDelete() {
    String credentialsId = insertKbCredentials(STUB_API_URL, STUB_CREDENTIALS_NAME, STUB_API_KEY, STUB_CUSTOMER_ID, vertx);

    String resourcePath = KB_CREDENTIALS_ENDPOINT + "/" + credentialsId;
    deleteWithNoContent(resourcePath);

    List<KbCredentials> kbCredentialsInDb = getKbCredentials(vertx);
    assertTrue(kbCredentialsInDb.isEmpty());
  }

  @Test
  public void shouldReturn400OnDeleteWhenHasRelatedRecords() {
    String credentialsId = insertKbCredentials(STUB_API_URL, STUB_CREDENTIALS_NAME, STUB_API_KEY, STUB_CUSTOMER_ID, vertx);
    insertAssignedUser(credentialsId, "username", "John", null, "Doe", "patron", vertx);

    String resourcePath = KB_CREDENTIALS_ENDPOINT + "/" + credentialsId;
    JsonapiError error = deleteWithStatus(resourcePath, SC_BAD_REQUEST).as(JsonapiError.class);

    assertEquals("Credentials have related records and can't be deleted", error.getErrors().get(0).getTitle());

    List<KbCredentials> kbCredentialsInDb = getKbCredentials(vertx);
    assertFalse(kbCredentialsInDb.isEmpty());
  }

  @Test
  public void shouldReturn201OnDeleteWhenCredentialsAreMissing() {
    String resourcePath = KB_CREDENTIALS_ENDPOINT + "/11111111-1111-1111-a111-111111111111";
    deleteWithNoContent(resourcePath);

    List<KbCredentials> kbCredentialsInDb = getKbCredentials(vertx);
    assertTrue(kbCredentialsInDb.isEmpty());
  }

  @Test
  public void shouldReturn400OnDeleteWhenIdIsInvalid() {
    String resourcePath = KB_CREDENTIALS_ENDPOINT + "/invalid-id";
    JsonapiError error = deleteWithStatus(resourcePath, SC_BAD_REQUEST).as(JsonapiError.class);

    assertThat(error.getErrors().get(0).getTitle(), containsString("'id' parameter is incorrect."));
  }

  private void stubForSuccessCredentials() {
    stubFor(
      get(urlPathMatching("/rm/rmaccounts/.*"))
        .willReturn(aResponse()
          .withStatus(SC_OK)
          .withBody("{\"totalResults\": 0, \"vendors\": []}")));
  }

  private void stubForFailedCredentials() {
    stubFor(
      get(urlPathMatching("/rm/rmaccounts/.*"))
        .willReturn(aResponse().withStatus(SC_UNPROCESSABLE_ENTITY)));
  }
}