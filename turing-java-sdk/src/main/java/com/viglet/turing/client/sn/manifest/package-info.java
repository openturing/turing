/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * T1030 / §XX.17 — declarative site provisioning from a connector's own schema.
 *
 * <p>{@link com.viglet.turing.client.sn.manifest.TurSNManifestClient} posts a
 * {@link com.viglet.turing.client.sn.manifest.TurSNManifest} built from the same
 * {@link com.viglet.turing.client.sn.job.TurSNJobAttributeSpec}s the connector
 * publishes with every document, so "configure the source and the site
 * provisions itself" holds for any Java connector.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
package com.viglet.turing.client.sn.manifest;
