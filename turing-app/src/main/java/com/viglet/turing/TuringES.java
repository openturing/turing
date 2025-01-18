/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.datatype.hibernate5.jakarta.Hibernate5JakartaModule;
import com.ulisesbocchio.jasyptspringboot.annotation.EnableEncryptableProperties;
import com.viglet.turing.console.TurConsole;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chroma.vectorstore.ChromaApi;
import org.springframework.ai.chroma.vectorstore.ChromaVectorStore;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.ollama.management.ModelManagementOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.CharacterEncodingFilter;

@Slf4j
@SpringBootApplication
@EnableJms
@EnableCaching
@EnableScheduling
@EnableEncryptableProperties
public class TuringES {
    private final OllamaApi ollamaApi = new OllamaApi();
    private final ObservationRegistry observationRegistry;
    public static final String UTF_8 = "UTF-8";
    public static final String CONSOLE = "console";

    public TuringES(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public static void main(String... args) {
        if (isConsole(args)) {
            new SpringApplicationBuilder(TurConsole.class).web(WebApplicationType.NONE).bannerMode(Banner.Mode.OFF)
                    .run(args);
        } else {
            SpringApplication.run(TuringES.class, args);
        }
    }

    private static boolean isConsole(String[] args) {
        return args != null && args.length > 0 && args[0].equals(CONSOLE);
    }

    @Bean
    FilterRegistrationBean<CharacterEncodingFilter> filterRegistrationBean() {
        FilterRegistrationBean<CharacterEncodingFilter> registrationBean = new FilterRegistrationBean<>();
        CharacterEncodingFilter characterEncodingFilter = new CharacterEncodingFilter();
        characterEncodingFilter.setForceEncoding(true);
        characterEncodingFilter.setEncoding(UTF_8);
        registrationBean.setFilter(characterEncodingFilter);
        return registrationBean;
    }

    @Bean
    Module hibernate5Module() {
        return new Hibernate5JakartaModule();
    }

/*
    @Bean
    public RestClient.Builder builder() {
        return RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory());
    }


    @Bean
    public ChromaApi chromaApi(RestClient.Builder restClientBuilder) {
        String chromaUrl = "http://localhost:8000";
        return new ChromaApi(chromaUrl, restClientBuilder);
    }

    @Bean
    public VectorStore chromaVectorStore(EmbeddingModel embeddingModel, ChromaApi chromaApi) {
        return ChromaVectorStore.builder(chromaApi, embeddingModel)
                .collectionName("langchain")
                .initializeSchema(true)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .ollamaApi(this.ollamaApi)
                .defaultOptions( OllamaOptions.builder()
                        .model(OllamaModel.MISTRAL.id())
                        .build())
                .modelManagementOptions(ModelManagementOptions.builder().build())
                .observationRegistry(observationRegistry).build();
    }

    @Bean
    public ChatModel chatModel() {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .defaultOptions(OllamaOptions.builder()
                        .model(OllamaModel.MISTRAL.id())
                        .build())
                .observationRegistry(observationRegistry)
                .modelManagementOptions(ModelManagementOptions.builder().build())
                .build();
    }
*/
}
