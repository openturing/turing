/*
 * Copyright (C) 2016-2025 the original author or authors.
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
package com.viglet.turing.artemis;

import com.viglet.core.messaging.VigletArtemisConfigurationCustomizer;
import com.viglet.turing.sn.TurSNConstants;
import org.springframework.stereotype.Component;

/**
 * Explicit Artemis broker tuning to avoid producer flow-control hangs.
 *
 * <p>Delegates to {@link VigletArtemisConfigurationCustomizer} (viglet-core),
 * pinning the same explicit limits and PAGE policy on the indexing queue and the
 * catch-all address so producers spill to disk instead of blocking on credit
 * acquisition between indexing runs.</p>
 */
@Component
public class TurArtemisConfig extends VigletArtemisConfigurationCustomizer {

    public TurArtemisConfig() {
        super(TurSNConstants.INDEXING_QUEUE);
    }
}
