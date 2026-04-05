package com.viglet.turing.service.git;

import java.util.List;

/**
 * Immutable snapshot of a git repository build pipeline status.
 *
 * @param state        current execution state
 * @param logLines     captured output lines from the build process
 * @param siteName     name of the deployed SPA page (populated on success)
 * @param errorMessage human-readable error description (populated on failure)
 * @param startedAt    ISO-8601 timestamp when the build started
 * @param completedAt  ISO-8601 timestamp when the build finished (success or fail)
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public record TurGitBuildStatus(
        TurGitBuildState state,
        List<String> logLines,
        String siteName,
        String errorMessage,
        String startedAt,
        String completedAt
) {
    public static TurGitBuildStatus idle() {
        return new TurGitBuildStatus(TurGitBuildState.IDLE, List.of(), null, null, null, null);
    }
}
