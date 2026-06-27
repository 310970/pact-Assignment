package com.ust.sdet.contract.oms;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactBroker;
import au.com.dius.pact.provider.junitsupport.loader.PactBrokerConsumerVersionSelectors;
import au.com.dius.pact.provider.junitsupport.loader.SelectorBuilder;

@Provider("oms-provider")
@PactBroker(
        url = "http://127.0.0.1:9292",
        enablePendingPacts = "true",
        providerTags = "main",
        includeWipPactsSince = "2026-06-26"
)
public class OmsProviderVerification {
    @RegisterExtension
    private static final WireMockExtension wireMock =
            WireMockExtension.newInstance()
                    .options(wireMockConfig().port(4010))
                    .build();


    @PactBrokerConsumerVersionSelectors
    public static SelectorBuilder
    consumerVersionSelectors() {
        return new SelectorBuilder()
                .mainBranch()
                .deployedOrReleased();
    }


    @BeforeEach
    void setup(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("127.0.0.1", 4010));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void verify(PactVerificationContext context) {
        context.verifyInteraction();
    }


    @State("Order 123 exists")
    void isOrderExists() {
        wireMock.stubFor(get(urlEqualTo("/orders/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                    {"id": 123, "status": "CONFIRMED", "total": 12.0}
                """)));
    }

    @State("Order created for inventory")
    void createOrder() {
        wireMock.stubFor(post(urlEqualTo("/orders/123"))
                .withHeader("Content-Type", matching("application/json(;.*)?"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                    {"quantity": 20, "sku": "SKU-9"}
                """)));
    }

    @State("Sku-9 has stock")
    void getInventory() {
        wireMock.stubFor(get(urlEqualTo("/Inventory/7"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                    {"id": 7, "status": "Confirmed", "total": 42}
                """)));
    }
}