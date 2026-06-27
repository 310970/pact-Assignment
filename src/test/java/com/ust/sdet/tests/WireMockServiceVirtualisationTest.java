package com.ust.sdet.tests;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

import static io.restassured.RestAssured.config;
import static io.restassured.RestAssured.given;
import static java.net.http.HttpResponse.BodyHandlers.ofString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class WireMockServiceVirtualisationTest {
    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private HttpClient client;

        @BeforeEach
        void ConsumerRole () {
            RestAssured.baseURI = wm.baseUrl();
            client = HttpClient.newHttpClient();

        }
    @Test
    @DisplayName("Exercise 1: Stub inventory with success and business conflict")
    void stubInventorySuccessAndBusinessConflict() {

        // Success case
        wm.stubFor(get(urlPathEqualTo("/inventory/SKU-9"))
                .willReturn(okJson("""
                {
                  "sku": "SKU-9",
                  "qty": 5
                }
                """)));

        // Business conflict case
        wm.stubFor(get(urlPathEqualTo("/inventory/SKU-0"))
                .willReturn(jsonResponse("""
                {
                  "error": "OUT_OF_STOCK"
                }
                """, 409)));

        // Verify success response
        given()
                .when()
                .get("/inventory/SKU-9")
                .then()
                .statusCode(200)
                .body("qty", equalTo(5))
                .body("sku", equalTo("SKU-9"));

        // Verify conflict response
        given()
                .when()
                .get("/inventory/SKU-0")
                .then()
                .statusCode(409)
                .body("error", equalTo("OUT_OF_STOCK"));

        // Verify SKU-9 endpoint called exactly once
        wm.verify(
                exactly(1),
                getRequestedFor(urlPathEqualTo("/inventory/SKU-9"))
        );
    }

    @Test
    @DisplayName("latency to test both the timeout and recovery")
    void MakeItSlow(){
            wm.stubFor(get(urlPathEqualTo("/orders/slow"))
                    .willReturn(okJson("""
                            {"id":1,"status":PENDING}
                            """).withFixedDelay(3000)));

            HttpRequest impatientRequest= HttpRequest.newBuilder()
                    .uri(URI.create(wm.baseUrl() +"/orders/slow"))
                    .timeout(Duration.ofSeconds(1))
                    .build();

            assertThrows(HttpTimeoutException.class,()->client.send(impatientRequest,ofString()));

            HttpRequest patientRequest= HttpRequest.newBuilder()
                    .uri(URI.create(wm.baseUrl()+"/orders/slow"))
                    .timeout(Duration.ofSeconds(5))
                    .build();
    }

    @Test
    @DisplayName("Single url with memory foundation for testing polling and retry then succeed")
    void ModeItStateful(){
            wm.stubFor(get(urlPathEqualTo("/orders/42")).inScenario("fulfillment")
                    .whenScenarioStateIs(STARTED)
                    .willReturn(okJson("""
                            {"status":"PENDING"}
                            """))
                    .willSetStateTo("CONFIRMED"));

        wm.stubFor(get(urlPathEqualTo("/orders/42")).inScenario("fulfillment")
                .whenScenarioStateIs("CONFIRMED")
                .willReturn(okJson("""
                            {"status":"CONFIRMED"}
                            """)));


            given()
                    .when()
                    .get("/orders/42")
                    .then()
                    .statusCode(200)
                    .body("status",equalTo("PENDING"));

            given()
                    .when()
                    .get("/orders/42")
                    .then()
                    .statusCode(200)
                            .body("status",equalTo("CONFIRMED"));
            wm.verify(exactly(2),getRequestedFor(urlPathEqualTo("/orders/42")));

    }


    }

