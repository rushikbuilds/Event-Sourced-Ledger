package com.example.ledger.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

  @Bean
  public RedisCacheConfiguration cacheConfiguration() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.activateDefaultTyping(
        BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
        ObjectMapper.DefaultTyping.EVERYTHING,
        JsonTypeInfo.As.PROPERTY
    );

    return RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofMinutes(5))
        .disableCachingNullValues()
        .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
        .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(mapper)));
  }

  @Bean
  public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer(RedisCacheConfiguration cacheConfiguration) {
    return builder -> builder
        .withCacheConfiguration("exchangeRates", cacheConfiguration.entryTtl(Duration.ofHours(1)))
        .withCacheConfiguration("accounts", cacheConfiguration.entryTtl(Duration.ofMinutes(10)))
        .withCacheConfiguration("accountsList", cacheConfiguration.entryTtl(Duration.ofMinutes(2)))
        .withCacheConfiguration("transactions", cacheConfiguration.entryTtl(Duration.ofMinutes(5)))
        .withCacheConfiguration("events", cacheConfiguration.entryTtl(Duration.ofMinutes(5)));
  }
}
