/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.distillation;

/**
 * F.9 / §X.10.d — T170. Binary operator preference on an assistant turn: the
 * raw signal the DPO dataset is built from.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public enum TurChatFeedbackRating {
    /** Thumb-up — a preferred ("chosen") answer in DPO terms. */
    UP,
    /** Thumb-down — a non-preferred ("rejected") answer in DPO terms. */
    DOWN
}
