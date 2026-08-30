package com.example.ledger.service;

import com.example.ledger.domain.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class ExchangeRateService {

  private final RestClient restClient;
  private final String apiUrl;

  public ExchangeRateService(@Value("${exchange.rate.api.url:https://open.er-api.com/v6/latest/}") String apiUrl) {
    this.apiUrl = apiUrl.endsWith("/") ? apiUrl : apiUrl + "/";
    this.restClient = RestClient.builder().build();
  }

  @Cacheable(value = "exchangeRates", key = "#fromCurrency.toUpperCase() + '_' + #toCurrency.toUpperCase()", unless = "#result == null")
  public BigDecimal getRate(String fromCurrency, String toCurrency) {
    if (fromCurrency == null || toCurrency == null) {
      throw new DomainException("INVALID_CURRENCY", "Currency cannot be null", 422);
    }
    String from = fromCurrency.toUpperCase().trim();
    String to = toCurrency.toUpperCase().trim();

    if (from.equals(to)) {
      return BigDecimal.ONE;
    }

    try {
      @SuppressWarnings("unchecked")
      Map<String, Object> response = restClient.get()
          .uri(apiUrl + from)
          .retrieve()
          .body(Map.class);

      if (response != null && response.containsKey("rates")) {
        @SuppressWarnings("unchecked")
        Map<String, Object> rates = (Map<String, Object>) response.get("rates");
        if (rates != null && rates.containsKey(to)) {
          Object rateVal = rates.get(to);
          return new BigDecimal(rateVal.toString());
        }
      }
      throw new DomainException("EXCHANGE_RATE_NOT_FOUND", "Exchange rate not found for " + from + " -> " + to, 502);
    } catch (DomainException e) {
      throw e;
    } catch (Exception e) {
      throw new DomainException("EXCHANGE_RATE_FETCH_FAILED", "Failed to fetch exchange rate: " + e.getMessage(), 502);
    }
  }
}
