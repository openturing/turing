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

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * T708 / §XLV — a locale the site can be searched in. The client switches by
 * setting the request's {@code locale} to {@link #getLocale()}; the legacy
 * locale {@code link} is gone.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Accessors(chain = true)
public class TurSNStructuredLocaleBean implements Serializable {

    /** Locale tag to put on the request, e.g. {@code pt_BR}. */
    private String locale;

    /** True for the locale this response was produced in. */
    private boolean selected;
}
