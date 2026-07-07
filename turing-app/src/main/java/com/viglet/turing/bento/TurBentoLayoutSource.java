/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.bento;

/**
 * T574 / §XXXI.11 — which cascade layer produced the resolved layout the client
 * received: the user's own override, the admin global template, or the built-in
 * {@code featured=idx0} default (empty entries — the client applies it).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurBentoLayoutSource {
    USER,
    GLOBAL,
    DEFAULT
}
