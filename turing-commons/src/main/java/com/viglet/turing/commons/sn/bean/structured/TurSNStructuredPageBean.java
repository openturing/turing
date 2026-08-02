/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.commons.sn.bean.structured;

import java.io.Serializable;

import com.viglet.turing.commons.sn.pagination.TurSNPaginationType;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * T708 / §XLV — one pagination control in the link-free response. The client
 * sets the request's {@code page} to {@link #getPage()}; the legacy {@code href}
 * is gone.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Accessors(chain = true)
public class TurSNStructuredPageBean implements Serializable {

    /** FIRST / PREVIOUS / PAGE / CURRENT / NEXT / LAST. */
    private TurSNPaginationType type;

    /** Display text (usually the page number). */
    private String text;

    /** The page to request. */
    private int page;

    /** True for the page currently being shown. */
    private boolean current;
}
