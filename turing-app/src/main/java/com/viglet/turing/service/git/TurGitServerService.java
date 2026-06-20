package com.viglet.turing.service.git;

import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.service.storage.TurStorageType;
import jakarta.annotation.PostConstruct;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing bare Git repositories stored in the
 * {@code repositories/} subdirectory of the active storage root.
 * <p>
 * Only available when {@code turing.git.server=true} and the storage type is
 * not {@code none}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Service
public class TurGitServerService {

    private static final Logger log = LoggerFactory.getLogger(TurGitServerService.class);
    public static final String REPOSITORIES_DIR = "repositories";

    private final TurConfigProperties configProperties;
    private final TurStorageService storageService;

    private Path reposBasePath;

    public TurGitServerService(TurConfigProperties configProperties, TurStorageService storageService) {
        this.configProperties = configProperties;
        this.storageService = storageService;
    }

    @PostConstruct
    void init() {
        if (!isEnabled()) {
            log.info("Git server is disabled (turing.git.server=false).");
            return;
        }
        reposBasePath = resolveReposBasePath();
        try {
            Files.createDirectories(reposBasePath);
            log.info("Git server initialized. Repositories directory: {}", reposBasePath);
        } catch (IOException e) {
            log.error("Failed to create git repositories directory: {}", reposBasePath, e);
        }
    }

    /** Returns true when git server is enabled (no longer requires storage to be active). */
    public boolean isEnabled() {
        return configProperties.getGit() != null && configProperties.getGit().isServer();
    }

    /**
     * Returns all repository names (directories ending with {@code .git} inside
     * the repositories root).
     */
    public List<TurGitRepositoryInfo> listRepositories() {
        requireEnabled();
        List<TurGitRepositoryInfo> result = new ArrayList<>();
        if (!Files.isDirectory(reposBasePath)) return result;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(reposBasePath, "*.git")) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    String name = entry.getFileName().toString();
                    String repoName = name.endsWith(".git") ? name.substring(0, name.length() - 4) : name;
                    result.add(new TurGitRepositoryInfo(repoName, name));
                }
            }
        } catch (IOException e) {
            log.error("Error listing git repositories", e);
        }
        return result;
    }

    /**
     * Creates a new bare git repository with the given name.
     *
     * @param name plain repository name (without {@code .git} suffix)
     * @throws IllegalArgumentException when the name is invalid
     * @throws IllegalStateException    when creation fails
     */
    public TurGitRepositoryInfo createRepository(String name) {
        requireEnabled();
        validateName(name);
        String dirName = name.endsWith(".git") ? name : name + ".git";
        Path repoPath = reposBasePath.resolve(dirName);
        if (Files.exists(repoPath)) {
            throw new IllegalArgumentException("Repository already exists: " + name);
        }
        try {
            Git.init().setBare(true).setDirectory(repoPath.toFile()).call();
            log.info("Created bare git repository: {}", repoPath);
            return new TurGitRepositoryInfo(name, dirName);
        } catch (GitAPIException e) {
            throw new IllegalStateException("Failed to create git repository: " + name, e);
        }
    }

    /**
     * Deletes an existing repository by name.
     *
     * @param name plain repository name (without {@code .git} suffix)
     */
    public void deleteRepository(String name) {
        requireEnabled();
        validateName(name);
        String dirName = name.endsWith(".git") ? name : name + ".git";
        Path repoPath = reposBasePath.resolve(dirName);
        if (!Files.isDirectory(repoPath)) {
            throw new IllegalArgumentException("Repository not found: " + name);
        }
        try {
            deleteRecursive(repoPath.toFile());
            log.info("Deleted git repository: {}", repoPath);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to delete git repository: " + name, e);
        }
    }

    /**
     * Returns the filesystem {@link Path} of the bare repository directory, or
     * {@code null} if the git server is not enabled.
     */
    public Path getRepoPath(String name) {
        requireEnabled();
        String dirName = name.endsWith(".git") ? name : name + ".git";
        return reposBasePath.resolve(dirName);
    }

    /** Returns the filesystem base path of the repositories directory. */
    public Path getReposBasePath() {
        return reposBasePath;
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private Path resolveReposBasePath() {
        if (storageService.isEnabled() && storageService.getType() == TurStorageType.FILESYSTEM) {
            // Place repositories inside the filesystem storage directory
            String fsPath = configProperties.getStorage() != null
                    && configProperties.getStorage().getFilesystem() != null
                    && configProperties.getStorage().getFilesystem().getPath() != null
                    ? configProperties.getStorage().getFilesystem().getPath()
                    : "./store/assets";
            return Path.of(fsPath).toAbsolutePath().normalize().resolve(REPOSITORIES_DIR);
        }
        // For MinIO or NONE: use local path for bare repos
        return Path.of("./store/" + REPOSITORIES_DIR).toAbsolutePath().normalize();
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Repository name must not be blank.");
        }
        // Reject path traversal and shell-dangerous characters
        if (name.contains("..") || name.contains("/") || name.contains("\\")
                || name.contains(" ") || !name.matches("[\\w.\\-]+")) {
            throw new IllegalArgumentException("Invalid repository name: " + name);
        }
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("Git server is not enabled.");
        }
    }

    private void deleteRecursive(File file) throws IOException {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        Files.delete(file.toPath());
    }
}
