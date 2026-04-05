package com.viglet.turing.service.git;

/**
 * Lightweight DTO representing a bare git repository managed by Turing.
 *
 * @param name    plain repository name (without {@code .git})
 * @param dirName directory name on disk (with {@code .git})
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public record TurGitRepositoryInfo(String name, String dirName) {
}
