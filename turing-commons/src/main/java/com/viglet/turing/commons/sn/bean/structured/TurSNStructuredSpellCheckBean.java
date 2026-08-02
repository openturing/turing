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
 * T708 / §XLV — spell-check in the link-free response.
 *
 * <p>
 * Both alternatives are expressed as request state rather than as a URL: the
 * client sets {@code q} to the chosen text, and — for "search for my original
 * term instead" — also sets {@code autoCorrectionDisabled}, which is exactly
 * what the legacy {@code nfpr=1} in the original's link encoded.
 * </p>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Getter
@Setter
@Accessors(chain = true)
public class TurSNStructuredSpellCheckBean implements Serializable {

    /** Whether the engine found a correction at all. */
    private boolean correctedText;

    /** Whether this response was produced from the corrected text. */
    private boolean usingCorrectedText;

    /** The term the user typed. Re-run it with {@code autoCorrectionDisabled = true}. */
    private String originalText;

    /** The engine's suggestion. Re-run it with {@code autoCorrectionDisabled = false}. */
    private String correctedTextValue;
}
