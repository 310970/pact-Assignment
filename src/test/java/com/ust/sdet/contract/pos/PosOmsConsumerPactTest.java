package com.ust.sdet.contract.pos;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslJsonBody;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.V4Pact;
import au.com.dius.pact.core.model.annotations.Pact;


@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(
        providerName = "oms-provider",
        pactVersion = PactSpecVersion.V4
)
class PosOmsConsumerPactTest {


    @Pact(
            provider = "oms-provider",
            consumer = "pos-consumer"
    )
    V4Pact getOrder(PactDslWithProvider builder) {

        return builder
                .given("Order 123 exists")

                .uponReceiving("a request for order 123")
                .path("/orders/123")
                .method("GET")

                .willRespondWith()
                .status(200)
                .matchHeader(
                        "Content-Type",
                        "application/json(;.*)?",
                        "application/json"
                )
                .body(new PactDslJsonBody()
                        .integerType("id",123)
                        .stringType("status","CONFIRMED")
                        .numberType("total",12.0)
                )

                .toPact(V4Pact.class);
    }



    @Pact(
            provider = "oms-provider",
            consumer = "pos-consumer"
    )
    V4Pact createOrder(PactDslWithProvider builder){

        return builder

                .given("Order created for inventory")

                .uponReceiving("a request for creating an Order")
                .path("/orders/123")
                .method("POST")

                .matchHeader(
                        "Content-Type",
                        "application/json(;.*)?",
                        "application/json"
                )

                .body(new PactDslJsonBody()
                        .stringType("sku","SKU-9")
                        .integerType("quantity",20)
                )

                .willRespondWith()
                .status(201)

                .matchHeader(
                        "Content-Type",
                        "application/json(;.*)?",
                        "application/json"
                )

                .body(new PactDslJsonBody()
                        .stringType("sku","SKU-9")
                        .integerType("quantity",20)
                )

                .toPact(V4Pact.class);
    }




    @Pact(
            provider = "oms-provider",
            consumer = "pos-consumer"
    )
    V4Pact getInventoryShow(PactDslWithProvider builder){

        return builder

                .given("Sku-9 has stock")

                .uponReceiving("a request for Sku-9")
                .path("/Inventory/7")
                .method("GET")

                .willRespondWith()
                .status(200)

                .matchHeader(
                        "Content-Type",
                        "application/json(;.*)?",
                        "application/json"
                )

                .body(new PactDslJsonBody()
                        .integerType("id",7)
                        .stringType("status","Confirmed")
                        .numberType("total",42)
                )

                .toPact(V4Pact.class);
    }





    @Test
    @PactTestFor(pactMethod = "getOrder")
    void testGetOrder(MockServer mockServer){


        OmsClient client =
                new OmsClient(mockServer.getUrl());


        OmsClient.Order order =
                client.getOrder(123);



        assertEquals(200,order.statuscode());
        assertEquals(123,order.orderId());
        assertEquals("CONFIRMED",order.status());
        assertEquals(12.0,order.total());

    }





    @Test
    @PactTestFor(pactMethod = "createOrder")
    void testCreateOrder(MockServer mockServer){


        OmsClient client =
                new OmsClient(mockServer.getUrl());


        OmsClient.CreateOrder order =
                client.createOrder(
                        "SKU-9",
                        20
                );



        assertEquals(201,order.statuscode());
        assertEquals("SKU-9",order.sku());
        assertEquals(20,order.quantity());

    }






    @Test
    @PactTestFor(pactMethod = "getInventoryShow")
    void testGetInventory(MockServer mockServer){


        OmsClient client =
                new OmsClient(mockServer.getUrl());


        OmsClient.Order inventory =
                client.getInventory(7);



        assertEquals(200,inventory.statuscode());
        assertEquals(7,inventory.orderId());
        assertEquals("Confirmed",inventory.status());
        assertEquals(42.0,inventory.total());

    }

}