package com.ust.sdet.contract.pos;


import static io.restassured.RestAssured.given;


import io.restassured.response.Response;



final class OmsClient {


    private final String baseUrl;



    OmsClient(String url){

        this.baseUrl = url;

    }





    Order getOrder(int id){


        Response response =
                given()
                        .baseUri(baseUrl)

                        .when()
                        .get("/orders/" + id);


        return new Order(

                response.statusCode(),

                ((Number) response.path("id")).intValue(),

                response.path("status"),

                ((Number) response.path("total")).doubleValue()

        );

    }






    CreateOrder createOrder(
            String sku,
            int quantity
    ){



        String jsonBody =
                """
                {
                  "sku":"%s",
                  "quantity":%d
                }
                """.formatted(
                        sku,
                        quantity
                );




        Response response =
                given()
                        .baseUri(baseUrl)
                        .contentType("application/json")
                        .body(jsonBody)

                        .when()
                        .post("/orders/123");





        return new CreateOrder(

                response.statusCode(),

                response.path("sku"),

                response.path("quantity")

        );

    }







    Order getInventory(int id){


        Response response =
                given()

                        .baseUri(baseUrl)

                        .when()

                        .get("/Inventory/" + id);



        return new Order(

                response.statusCode(),

                ((Number) response.path("id")).intValue(),

                response.path("status"),

                ((Number) response.path("total")).doubleValue()

        );

    }






    record Order(
            int statuscode,
            int orderId,
            String status,
            double total
    ){}





    record CreateOrder(
            int statuscode,
            String sku,
            int quantity
    ){}


}