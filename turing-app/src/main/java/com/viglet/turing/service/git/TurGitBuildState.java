package com.viglet.turing.service.git;

/**
 * States for a git repository build pipeline execution.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public enum TurGitBuildState {
    IDLE, RUNNING, COMPLETED, FAILED
}
