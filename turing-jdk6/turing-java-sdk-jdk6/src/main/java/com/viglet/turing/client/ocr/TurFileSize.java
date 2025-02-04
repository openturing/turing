package com.viglet.turing.client.ocr;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TurFileSize {
    private float bytes;
    private float kiloBytes;
    private float megaBytes;
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

    public float getBytes() {
        return bytes;
    }

    public void setBytes(float bytes) {
        this.bytes = bytes;
    }

    public float getKiloBytes() {
        return kiloBytes;
    }

    public void setKiloBytes(float kiloBytes) {
        this.kiloBytes = kiloBytes;
    }

    public float getMegaBytes() {
        return megaBytes;
    }

    public void setMegaBytes(float megaBytes) {
        this.megaBytes = megaBytes;
    }
}
