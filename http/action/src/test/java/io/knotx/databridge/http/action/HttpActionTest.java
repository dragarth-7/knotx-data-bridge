/*
 * Copyright (C) 2019 Knot.x Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.knotx.databridge.http.action;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.knotx.fragments.handler.api.domain.FragmentResult.ERROR_TRANSITION;
import static io.knotx.fragments.handler.api.domain.FragmentResult.SUCCESS_TRANSITION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.knotx.databridge.http.action.common.configuration.EndpointOptions;
import io.knotx.fragment.Fragment;
import io.knotx.fragments.handler.api.domain.FragmentContext;
import io.knotx.fragments.handler.api.domain.FragmentResult;
import io.knotx.fragments.handler.api.domain.payload.ActionPayload;
import io.knotx.fragments.handler.api.domain.payload.ActionRequest;
import io.knotx.fragments.handler.api.domain.payload.ActionResponse;
import io.knotx.fragments.handler.api.domain.payload.ActionResponseError;
import io.knotx.server.api.context.ClientRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.reactivex.core.MultiMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(VertxExtension.class)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class HttpActionTest {

  private static final String VALID_REQUEST_PATH = "/valid-service";
  private static final String VALID_JSON_RESPONSE_BODY = "{ \"data\": \"service response\"}";
  private static final String VALID_JSON_ARRAY_RESPONSE_BODY = "[ \"first service response\", \" second service response\"]";
  private static final String VALID_EMPTY_RESPONSE_BODY = "";

  private static final Fragment FRAGMENT = new Fragment("type", new JsonObject(), "expectedBody");
  private static final String ACTION_ALIAS = "httpAction";

  @Mock
  private ClientRequest clientRequest;

  private WireMockServer wireMockServer;

  @BeforeEach
  void setUp() {
    this.wireMockServer = new WireMockServer(options().dynamicPort());
    this.wireMockServer.start();
  }

  @Test
  @DisplayName("Expect success transition when endpoint returned success status code")
  void expectSuccessTransitionWhenSuccessResponse(VertxTestContext testContext, Vertx vertx)
      throws Throwable {
    // given, when
    HttpAction tested = successAction(vertx, VALID_REQUEST_PATH, VALID_JSON_RESPONSE_BODY);

    // then
    verifyExecution(tested,
        fragmentResult -> assertEquals(SUCCESS_TRANSITION, fragmentResult.getTransition()),
        testContext);
  }

  @Test
  @DisplayName("Expect action alias key in fragment payload when endpoint responded with success status code")
  void appendActionAliasToPayload(VertxTestContext testContext, Vertx vertx)
      throws Throwable {
    // given
    HttpAction tested = successAction(vertx, VALID_REQUEST_PATH, VALID_JSON_RESPONSE_BODY);

    // then
    verifyExecution(tested,
        fragmentResult -> assertTrue(
            fragmentResult.getFragment().getPayload().containsKey(ACTION_ALIAS)),
        testContext);
  }

  @Test
  @DisplayName("Expect fragment payload appended with endpoint result when endpoint responded with success status code and JSON body")
  void appendPayloadWhenEndpointResponseWithJsonObject(VertxTestContext testContext, Vertx vertx)
      throws Throwable {
    // given, when
    HttpAction tested = successAction(vertx, VALID_REQUEST_PATH, VALID_JSON_RESPONSE_BODY);

    // then
    verifyExecution(tested, fragmentResult -> {
      ActionPayload payload = new ActionPayload(
          fragmentResult.getFragment().getPayload().getJsonObject(ACTION_ALIAS));
      assertTrue(payload.getResponse().isSuccess());
      assertEquals(new JsonObject(VALID_JSON_RESPONSE_BODY), payload.getResult());
    }, testContext);
  }

  @Test
  @DisplayName("Expect fragment payload appended with endpoint result when endpoint responded with success status code and JSONArray body")
  void appendPayloadWhenEndpointResponseWithJsonArrayVertxTestContext(VertxTestContext testContext,
      Vertx vertx) throws Throwable {
    // given, when
    HttpAction tested = successAction(vertx, VALID_REQUEST_PATH, VALID_JSON_ARRAY_RESPONSE_BODY);

    // then
    verifyExecution(tested, fragmentResult -> {
      ActionPayload payload = new ActionPayload(
          fragmentResult.getFragment().getPayload().getJsonObject(ACTION_ALIAS));
      assertTrue(payload.getResponse().isSuccess());
      assertEquals(new JsonArray(VALID_JSON_ARRAY_RESPONSE_BODY), payload.getResult());
    }, testContext);
  }

  @Test
  @DisplayName("Expect fragment's body not modified when endpoint responded with OK and empty body")
  void fragmentsBodyNotModifiedWhenEmptyResponseBody(VertxTestContext testContext,
      Vertx vertx) throws Throwable {
    // given, when
    HttpAction tested = successAction(vertx, VALID_REQUEST_PATH, VALID_EMPTY_RESPONSE_BODY);

    // then
    verifyExecution(tested,
        fragmentResult -> assertEquals(FRAGMENT.getBody(), fragmentResult.getFragment().getBody()),
        testContext);
  }

  @Test
  @DisplayName("Expect response metadata in payload when endpoint returned success status code")
  void responseMetadataInPayloadWhenSuccessResponse(VertxTestContext testContext,
      Vertx vertx) throws Throwable {
    // given, when
    HttpAction tested = successAction(vertx, VALID_REQUEST_PATH, VALID_JSON_RESPONSE_BODY);

    // then
    verifyExecution(tested, fragmentResult -> {
      ActionPayload payload = new ActionPayload(
          fragmentResult.getFragment().getPayload().getJsonObject(ACTION_ALIAS));
      ActionResponse response = payload.getResponse();
      assertNotNull(response);
      assertTrue(response.isSuccess());
      JsonObject metadata = response.getMetadata();
      assertNotNull(metadata);
      assertEquals("200", metadata.getString("statusCode"));
    }, testContext);
  }

  @Test
  @DisplayName("Expect response metadata in payload when endpoint returned error status code")
  void responseMetadataInPayloadWhenErrorResponse(VertxTestContext testContext,
      Vertx vertx) throws Throwable {
    // given, when
    HttpAction tested = errorAction(vertx, VALID_REQUEST_PATH, null, 500, "Internal Error");

    // then
    verifyExecution(tested, fragmentResult -> {
      ActionPayload payload = new ActionPayload(
          fragmentResult.getFragment().getPayload().getJsonObject(ACTION_ALIAS));
      ActionResponse response = payload.getResponse();
      assertFalse(response.isSuccess());
      ActionResponseError error = response.getError();
      assertNotNull(error);
      assertEquals("ERROR", error.getCode());
      assertEquals("Internal Error", error.getMessage());
      JsonObject metadata = response.getMetadata();
      assertNotNull(metadata);
      assertEquals("500", metadata.getString("statusCode"));

    }, testContext);
  }

  @Test
  @DisplayName("Expect request metadata in payload when endpoint returned success status code")
  void requestMetadataInPayloadWhenSuccessResponse(VertxTestContext testContext,
      Vertx vertx) throws Throwable {
    // given, when
    HttpAction tested = successAction(vertx, VALID_REQUEST_PATH, VALID_JSON_RESPONSE_BODY);

    // then
    verifyExecution(tested, fragmentResult -> {
      ActionPayload payload = new ActionPayload(
          fragmentResult.getFragment().getPayload().getJsonObject(ACTION_ALIAS));
      ActionRequest request = payload.getRequest();
      assertNotNull(request);
      assertEquals("HTTP", request.getType());
      assertEquals(VALID_REQUEST_PATH, request.getSource());
    }, testContext);
  }

  @Test
  @DisplayName("Expect request metadata in payload when endpoint returned error status code")
  void requestMetadataInPayloadWhenErrorResponse(VertxTestContext testContext,
      Vertx vertx) throws Throwable {
    // given, when
    HttpAction tested = errorAction(vertx, VALID_REQUEST_PATH, null, 500, "Internal Error");

    // then
    verifyExecution(tested, fragmentResult -> {
      ActionPayload payload = new ActionPayload(
          fragmentResult.getFragment().getPayload().getJsonObject(ACTION_ALIAS));
      ActionRequest request = payload.getRequest();
      assertNotNull(request);
      assertEquals("HTTP", request.getType());
      assertEquals(VALID_REQUEST_PATH, request.getSource());
    }, testContext);

  }

  @Test
  @DisplayName("Expect error transition when endpoint returned error status code")
  void errorTransitionWhenErrorStatusCode(VertxTestContext testContext,
      Vertx vertx) throws Throwable {
    // given, when
    HttpAction tested = errorAction(vertx, VALID_REQUEST_PATH, null, 500, "Internal Error");

    // then
    verifyExecution(tested,
        fragmentResult -> assertEquals(ERROR_TRANSITION, fragmentResult.getTransition()),
        testContext);
  }

  @Test
  @DisplayName("Expect error transition when endpoint returned not valid JSON")
  void errorTransitionWhenResponseIsNotJson() {

  }

  @Test
  @DisplayName("Expect error transition when endpoint times out")
  void errorTransitionWhenEndpointTimesOut() {

  }

  @Test
  @DisplayName("Expect error transition when calling not existing endpoint")
  void errorTransitionWhenEndpointDoesNotExist() {

  }

  @Test
  @DisplayName("Expect headers from FragmentContext clientRequest are filtered and sent in endpoint request")
  void headersFromClientRequestFilteredAndSendToEndpoint() {

  }

  @Test
  @DisplayName("Expect additionalHeaders from EndpointOptions are sent in endpoint request")
  void additionalHeadersSentToEndpoint() {

  }

  @Test
  @DisplayName("Expect additionalHeaders override headers from FragmentContext clientRequest")
  void additionalHeadersOverrideClientRequestHeaders() {
  }

  @Test
  @DisplayName("Expect endpoint called with placeholders in path resolved with values from headers from FragmentContext clientRequest")
  void placeholdersInPathResolvedWithHeadersValues() {

  }

  @Test
  @DisplayName("Expect endpoint called with placeholders in path resolved with values from FragmentContext clientRequest query params")
  void placehodersInPathResolvedWithClientRequestQueryParams() {

  }

  private HttpAction successAction(Vertx vertx, String requestPath, String responseBody) {
    return getHttpAction(vertx, requestPath, responseBody, HttpResponseStatus.OK.code(), null);
  }

  private HttpAction errorAction(Vertx vertx, String requestPath, String responseBody,
      int statusCode, String statusMessage) {
    return getHttpAction(vertx, requestPath, responseBody, statusCode, statusMessage);
  }

  private HttpAction getHttpAction(Vertx vertx, String requestPath, String responseBody,
      int statusCode, String statusMessage) {
    wireMockServer.stubFor(get(urlEqualTo(requestPath))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)
            .withStatus(statusCode)
            .withStatusMessage(statusMessage)));
    when(clientRequest.getPath()).thenReturn(requestPath);
    when(clientRequest.getHeaders()).thenReturn(MultiMap.caseInsensitiveMultiMap());

    EndpointOptions endpointOptions = new EndpointOptions().setPath(requestPath)
        .setDomain("localhost").setPort(wireMockServer.port());

    return new HttpAction(vertx,
        new HttpActionOptions().setEndpointOptions(endpointOptions), ACTION_ALIAS);
  }

  private void verifyExecution(HttpAction tested, Consumer<FragmentResult> assertions,
      VertxTestContext testContext) throws Throwable {
    tested.apply(new FragmentContext(FRAGMENT, clientRequest),
        testContext.succeeding(result -> {
          testContext.verify(() -> assertions.accept(result));
          testContext.completeNow();
        }));

    //then
    assertTrue(testContext.awaitCompletion(5, TimeUnit.SECONDS));
    if (testContext.failed()) {
      throw testContext.causeOfFailure();
    }
  }
}