package com.viglet.turing.filesystem.commons;


import lombok.*;

import java.math.BigDecimal;
import java.math.RoundingMode;

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
    public TurFileSize(float bytes) {
        this.bytes = twoDecimalFloat(bytes);
        this.kiloBytes = twoDecimalFloat(this.bytes / 1024);
        this.megaBytes = twoDecimalFloat(this.kiloBytes / 1024);
    }

    private float twoDecimalFloat(float value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).floatValue();
    }

}
