package com.viglet.turing.service.asset;

/**
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
public record TurAssetTrainingStatus(
        TurAssetTrainingState state,
        int totalCount,
        int processedCount,
        int errorCount,
        String startedAt,
        String completedAt,
        String errorMessage
) {
    public static TurAssetTrainingStatus idle() {
        return new TurAssetTrainingStatus(TurAssetTrainingState.IDLE, 0, 0, 0, "", "", "");
    }
}
