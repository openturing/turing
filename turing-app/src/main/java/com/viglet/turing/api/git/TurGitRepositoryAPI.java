package com.viglet.turing.api.git;

import com.viglet.turing.service.git.TurGitBrowseService;
import com.viglet.turing.service.git.TurGitBuildStatus;
import com.viglet.turing.service.git.TurGitPipelineService;
import com.viglet.turing.service.git.TurGitRepositoryInfo;
import com.viglet.turing.service.git.TurGitServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for managing Git repositories hosted by Turing.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@RestController
@RequestMapping("/api/git/repository")
@Tag(name = "Git Repository", description = "Git repository management API")
public class TurGitRepositoryAPI {

    private final TurGitServerService gitServerService;
    private final TurGitPipelineService gitPipelineService;
    private final TurGitBrowseService gitBrowseService;

    public TurGitRepositoryAPI(TurGitServerService gitServerService,
                               TurGitPipelineService gitPipelineService,
                               TurGitBrowseService gitBrowseService) {
        this.gitServerService = gitServerService;
        this.gitPipelineService = gitPipelineService;
        this.gitBrowseService = gitBrowseService;
    }

    @Operation(summary = "List all Git repositories")
    @GetMapping
    @Secured({"ROLE_ADMIN", "ROLE_USER"})
    public ResponseEntity<List<TurGitRepositoryInfo>> list() {
        if (!gitServerService.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(gitServerService.listRepositories());
    }

    @Operation(summary = "Create a new Git repository")
    @PostMapping
    @Secured({"ROLE_ADMIN"})
    public ResponseEntity<TurGitRepositoryInfo> create(@RequestBody TurGitRepositoryRequest request) {
        if (!gitServerService.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        TurGitRepositoryInfo info = gitServerService.createRepository(request.name());
        return ResponseEntity.ok(info);
    }

    @Operation(summary = "Delete a Git repository")
    @DeleteMapping("/{name}")
    @Secured({"ROLE_ADMIN"})
    public ResponseEntity<Void> delete(@PathVariable String name) {
        if (!gitServerService.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        gitServerService.deleteRepository(name);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Start the npm build pipeline for a Git repository and deploy as a Pages SPA")
    @PostMapping("/{name}/build")
    @Secured({"ROLE_ADMIN"})
    public ResponseEntity<TurGitBuildStatus> startBuild(@PathVariable String name) {
        if (!gitServerService.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        TurGitBuildStatus status = gitPipelineService.startBuild(name);
        return ResponseEntity.ok(status);
    }

    @Operation(summary = "Get the current build pipeline status for a Git repository")
    @GetMapping("/{name}/build/status")
    @Secured({"ROLE_ADMIN", "ROLE_USER"})
    public ResponseEntity<TurGitBuildStatus> getBuildStatus(@PathVariable String name) {
        if (!gitServerService.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(gitPipelineService.getStatus(name));
    }

    // ── Browse endpoints ──

    @Operation(summary = "List branches of a repository")
    @GetMapping("/{name}/branches")
    @Secured({"ROLE_ADMIN", "ROLE_USER"})
    public ResponseEntity<List<TurGitBrowseService.GitBranch>> listBranches(@PathVariable String name) {
        if (!gitServerService.isEnabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(gitBrowseService.listBranches(name));
    }

    @Operation(summary = "List files and directories in a repository tree")
    @GetMapping("/{name}/tree")
    @Secured({"ROLE_ADMIN", "ROLE_USER"})
    public ResponseEntity<List<TurGitBrowseService.GitTreeEntry>> listTree(
            @PathVariable String name,
            @RequestParam(required = false) String ref,
            @RequestParam(required = false) String path) {
        if (!gitServerService.isEnabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(gitBrowseService.listTree(name, ref, path));
    }

    @Operation(summary = "Read file content from a repository")
    @GetMapping("/{name}/file")
    @Secured({"ROLE_ADMIN", "ROLE_USER"})
    public ResponseEntity<byte[]> readFile(
            @PathVariable String name,
            @RequestParam String path,
            @RequestParam(required = false) String ref) {
        if (!gitServerService.isEnabled()) return ResponseEntity.notFound().build();
        return gitBrowseService.readFile(name, ref, path)
                .map(content -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(content))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Request body for creating a repository.
     *
     * @param name plain repository name (without {@code .git})
     */
    public record TurGitRepositoryRequest(String name) {
    }
}
