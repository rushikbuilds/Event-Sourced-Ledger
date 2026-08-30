package com.example.ledger.service;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ExchangeRateServiceTest {

  @Test
  void sameCurrencyReturnsOne() {
    ExchangeRateService service = new ExchangeRateService("https://open.er-api.com/v6/latest/");
    BigDecimal rate = service.getRate("USD", "USD");
    assertEquals(BigDecimal.ONE, rate);

    BigDecimal inrRate = service.getRate("INR", "INR");
    assertEquals(BigDecimal.ONE, inrRate);
  }

  @Test
  void nullCurrencyThrowsException() {
    ExchangeRateService service = new ExchangeRateService("https://open.er-api.com/v6/latest/");
    assertThrows(Exception.class, () -> service.getRate(null, "USD"));
    assertThrows(Exception.class, () -> service.getRate("USD", null));
  }
}
