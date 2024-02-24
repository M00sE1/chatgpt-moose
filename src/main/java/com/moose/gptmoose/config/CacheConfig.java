package com.moose.gptmoose.config;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.theokanning.openai.completion.chat.ChatMessage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

/**
 * @author Moose
 */
@Configuration
public class CacheConfig {
    @Bean
    public Cache<String, LinkedList<ChatMessage>> cache() {
        return CacheBuilder.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build();
    }
}