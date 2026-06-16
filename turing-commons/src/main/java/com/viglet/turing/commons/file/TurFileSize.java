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

import com.viglet.core.commons.file.VigletFileSize;

import lombok.*;

/**
 * @author Alexandre Oliveira
 * @since 0.3.9
 **/
@Builder
@Setter
@Getter
@AllArgsConstructor
@ToString
public class TurFileSize {
    private final float bytes;
    private final float kiloBytes;
    private final float megaBytes;
    public TurFileSize() {
        this(0f);
    }
    // Delegates the byte->KB->MB conversion to the neutral VigletFileSize
    // (Block Q / T375) so the rounding logic lives in viglet-core-commons,
    // while this DTO keeps its Tur* name, builder and all-args constructor.
    public TurFileSize(float bytes) {
        VigletFileSize size = new VigletFileSize(bytes);
        this.bytes = size.getBytes();
        this.kiloBytes = size.getKiloBytes();
        this.megaBytes = size.getMegaBytes();
    }

}
