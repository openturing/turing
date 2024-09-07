/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.commons.file;

import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @author Alexandre Oliveira
 * @since 0.3.9
 **/
@Getter
public class TurFileSize {
    private final float bytes;
    private final float kiloBytes;
    private final float megaBytes;

    public TurFileSize(float bytes) {
        this.bytes = twoDecimalFloat(bytes);
        this.kiloBytes = twoDecimalFloat(this.bytes / 1024);
        this.megaBytes = twoDecimalFloat(this.kiloBytes / 1024);
    }

    private float twoDecimalFloat(float value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).floatValue();
    }

}
