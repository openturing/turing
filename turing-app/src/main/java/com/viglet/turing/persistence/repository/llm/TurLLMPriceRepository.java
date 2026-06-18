/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.repository.llm;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.viglet.turing.persistence.model.llm.TurLLMPrice;

/**
 * T289 / §XVI.1 — CRUD + lookup for the per-model price table. The hot-path
 * lookup ({@code findByVendorIdAndModelName}) is cached at the service layer
 * (cache {@code turLlmPrice}) rather than here so the price service owns both
 * the read cache and its eviction on write.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public interface TurLLMPriceRepository extends JpaRepository<TurLLMPrice, String> {

    Optional<TurLLMPrice> findByVendorIdAndModelName(String vendorId, String modelName);

    List<TurLLMPrice> findByOrderByVendorIdAscModelNameAsc();
}
