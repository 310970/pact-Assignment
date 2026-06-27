package com.ust.sdet.tests;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

import static io.restassured.RestAssured.config;
import static io.restassured.RestAssured.given;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EndToEndOrderFlowTest {

   @RegisterExtension
   static WireMockExtension wm=WireMockExtension.newInstance()
           .options(wireMockConfig().dynamicPort())
           .build();

   private HttpClient client;


   @BeforeEach
    void setup(){
    RestAssured.baseURI= wm.baseUrl();
    client=HttpClient.newHttpClient();
    }

    @Test
    @DisplayName("End to End Order Fulfillment Journey")
    void completeOrderFulfillmentJourney() throws Exception {

        // =========================================================
        // STEP 1 : Inventory Service Virtualization
        // =========================================================

        wm.stubFor(get(urlPathEqualTo("/inventory/SKU-9"))
                .willReturn(okJson("""
                        {
                          "sku":"SKU-9",
                          "qty":5
                        }
                        """)));

        // =========================================================
        // STEP 2 : Create Order
        // =========================================================

        wm.stubFor(post(urlPathEqualTo("/orders"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "id":42,
                                  "status":"PENDING"
                                }
                                """)));

        // =========================================================
        // STEP 3 : Stateful Order Status
        // =========================================================

        // First Poll -> PENDING
        wm.stubFor(get(urlPathEqualTo("/orders/42"))
                .inScenario("Order Fulfillment")
                .whenScenarioStateIs(STARTED)
                .willReturn(okJson("""
                        {
                          "id":42,
                          "status":"PENDING"
                        }
                        """))
                .willSetStateTo("CONFIRMED"));

        // Second Poll -> CONFIRMED
        wm.stubFor(get(urlPathEqualTo("/orders/42"))
                .inScenario("Order Fulfillment")
                .whenScenarioStateIs("CONFIRMED")
                .willReturn(okJson("""
                        {
                          "id":42,
                          "status":"CONFIRMED"
                        }
                        """)));

        // =========================================================
        // STEP 4 : Shipping Service Delay (Timeout Simulation)
        // =========================================================

        wm.stubFor(get(urlPathEqualTo("/shipping/42"))
                .willReturn(okJson("""
                        {
                          "shipment":"DISPATCHED"
                        }
                        """).withFixedDelay(3000)));

        // =========================================================
        // STEP 5 : Additional Variation
        // Simulate Payment Failure Then Recovery
        // =========================================================

        // First call -> failure
        wm.stubFor(get(urlPathEqualTo("/payment/42"))
                .inScenario("Payment Recovery")
                .whenScenarioStateIs(STARTED)
                .willReturn(jsonResponse("""
                        {
                          "error":"PAYMENT_GATEWAY_DOWN"
                        }
                        """, 503))
                .willSetStateTo("PAYMENT_OK"));

        // Second call -> success
        wm.stubFor(get(urlPathEqualTo("/payment/42"))
                .inScenario("Payment Recovery")
                .whenScenarioStateIs("PAYMENT_OK")
                .willReturn(okJson("""
                        {
                          "payment":"SUCCESS"
                        }
                        """)));

        // =========================================================
        // EXECUTION FLOW
        // =========================================================

        // Inventory validation
        given()
                .when()
                .get("/inventory/SKU-9")
                .then()
                .statusCode(200)
                .body("sku", equalTo("SKU-9"))
                .body("qty", equalTo(5));

        // Create order
        given()
                .header("Content-Type", "application/json")
                .body("""
                        {
                          "sku":"SKU-9",
                          "qty":1
                        }
                        """)
                .when()
                .post("/orders")
                .then()
                .statusCode(201)
                .body("id", equalTo(42))
                .body("status", equalTo("PENDING"));

        // First poll -> pending
        given()
                .when()
                .get("/orders/42")
                .then()
                .statusCode(200)
                .body("status", equalTo("PENDING"));

        // Second poll -> confirmed
        given()
                .when()
                .get("/orders/42")
                .then()
                .statusCode(200)
                .body("status", equalTo("CONFIRMED"));

        // =========================================================
        // SHIPPING TIMEOUT TEST
        // =========================================================

        HttpRequest impatientRequest = HttpRequest.newBuilder()
                .uri(URI.create(wm.baseUrl() + "/shipping/42"))
                .timeout(Duration.ofSeconds(1))
                .build();

        assertThrows(
                HttpTimeoutException.class,
                () -> client.send(impatientRequest, ofString())
        );

        HttpRequest patientRequest = HttpRequest.newBuilder()
                .uri(URI.create(wm.baseUrl() + "/shipping/42"))
                .timeout(Duration.ofSeconds(5))
                .build();

        assertEquals(
                200,
                client.send(patientRequest, ofString()).statusCode()
        );

        // =========================================================
        // PAYMENT FAILURE THEN RECOVERY
        // =========================================================

        // First call -> 503
        given()
                .when()
                .get("/payment/42")
                .then()
                .statusCode(503)
                .body("error", equalTo("PAYMENT_GATEWAY_DOWN"));

        // Second call -> success
        given()
                .when()
                .get("/payment/42")
                .then()
                .statusCode(200)
                .body("payment", equalTo("SUCCESS"));

        // =========================================================
        // VERIFICATION
        // =========================================================

        wm.verify(exactly(1),
                getRequestedFor(urlPathEqualTo("/inventory/SKU-9")));

        wm.verify(exactly(1),
                postRequestedFor(urlPathEqualTo("/orders")));

        wm.verify(exactly(2),
                getRequestedFor(urlPathEqualTo("/orders/42")));

        wm.verify(exactly(2),
                getRequestedFor(urlPathEqualTo("/payment/42")));
    }
}