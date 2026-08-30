package com.example.ledger;

import io.restassured.RestAssured;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LedgerApplicationIT {
  @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
  @Container static final org.testcontainers.containers.GenericContainer<?> REDIS = new org.testcontainers.containers.GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
  @LocalServerPort int port;

  @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Test void opensAndReadsAnAccount() {
    RestAssured.port = port;
    String id = given().contentType("application/json").body("{\"ownerName\":\"Alice\",\"initialDeposit\":100}")
      .when().post("/api/accounts").then().statusCode(201).body("balance", equalTo(100)).extract().path("accountId");
    given().when().get("/api/accounts/{id}", id).then().statusCode(200).body("ownerName", equalTo("Alice"));
  }

  @Test void transferBetweenAccounts() {
    RestAssured.port = port;
    String id1 = given().contentType("application/json").body("{\"ownerName\":\"Alice\",\"currency\":\"USD\",\"initialDeposit\":500}")
      .when().post("/api/accounts").then().statusCode(201).extract().path("accountId");
    String id2 = given().contentType("application/json").body("{\"ownerName\":\"Bob\",\"currency\":\"USD\",\"initialDeposit\":100}")
      .when().post("/api/accounts").then().statusCode(201).extract().path("accountId");

    given().contentType("application/json").body("{\"fromAccountId\":\"" + id1 + "\",\"toAccountId\":\"" + id2 + "\",\"amount\":100,\"description\":\"Payment\"}")
      .when().post("/api/transfers").then().statusCode(200).body("exchangeRate", equalTo(1));

    given().when().get("/api/accounts/{id}", id1).then().statusCode(200).body("balance", equalTo(400.0f));
    given().when().get("/api/accounts/{id}", id2).then().statusCode(200).body("balance", equalTo(200.0f));
  }
}
