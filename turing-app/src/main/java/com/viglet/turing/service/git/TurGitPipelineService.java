package com.viglet.turing.service.git;

import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.service.storage.TurStorageContentTypes;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Orchestrates the npm build pipeline for a git repository and deploys the
 * output as a Pages SPA site.
 *
 * <p>Pipeline steps:
 * <ol>
 *   <li>Clone the bare repository into a temp working directory.</li>
 *   <li>Run {@code npm install} followed by {@code npm run build}.</li>
 *   <li>Locate the build output ({@code dist/}, {@code build/}, or {@code out/}).</li>
 *   <li>ZIP the build output and deploy it to storage under {@code public/<repoName>/}.</li>
 * </ol>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Service
public class TurGitPipelineService {

    private static final Logger log = LoggerFactory.getLogger(TurGitPipelineService.class);
    private static final String PAGES_PREFIX = "public/";
    private static final List<String> BUILD_OUTPUT_DIRS = List.of("dist", "build", "out", "public", ".output/public");

    private final TurGitServerService gitServerService;
    private final TurStorageService storageService;

    /** Per-repository build status keyed by repository name. */
    private final ConcurrentHashMap<String, AtomicReference<TurGitBuildStatus>> statusMap =
            new ConcurrentHashMap<>();

    private final ExecutorService executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "git-pipeline");
        t.setDaemon(true);
        return t;
    });

    public TurGitPipelineService(TurGitServerService gitServerService, TurStorageService storageService) {
        this.gitServerService = gitServerService;
        this.storageService = storageService;
    }

    /**
     * Returns the current build status for the given repository, or
     * {@link TurGitBuildStatus#idle()} if no build has been run yet.
     */
    public TurGitBuildStatus getStatus(String repoName) {
        AtomicReference<TurGitBuildStatus> ref = statusMap.get(repoName);
        return ref != null ? ref.get() : TurGitBuildStatus.idle();
    }

    /**
     * Starts the build pipeline for the given repository asynchronously.
     *
     * @return the initial {@link TurGitBuildStatus} (state = RUNNING)
     * @throws IllegalStateException when the git server is not enabled or a build is already running
     */
    public TurGitBuildStatus startBuild(String repoName) {
        if (!gitServerService.isEnabled()) {
            throw new IllegalStateException("Git server is not enabled.");
        }
        AtomicReference<TurGitBuildStatus> ref = statusMap.computeIfAbsent(
                repoName, k -> new AtomicReference<>(TurGitBuildStatus.idle()));

        TurGitBuildStatus current = ref.get();
        if (current.state() == TurGitBuildState.RUNNING) {
            return current;
        }

        String startedAt = Instant.now().toString();
        List<String> logAccumulator = Collections.synchronizedList(new ArrayList<>());
        TurGitBuildStatus running = new TurGitBuildStatus(
                TurGitBuildState.RUNNING, List.copyOf(logAccumulator), null, null, startedAt, null);
        ref.set(running);

        executor.submit(() -> doBuild(repoName, ref, logAccumulator, startedAt));
        return ref.get();
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private void doBuild(String repoName, AtomicReference<TurGitBuildStatus> ref,
                         List<String> logLines, String startedAt) {
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("turing-git-build-" + repoName + "-");
            addLog(logLines, ref, startedAt, "=== Starting build for repository: " + repoName + " ===");

            // Step 1: clone the bare repo into the work dir
            Path repoPath = gitServerService.getRepoPath(repoName);
            if (!Files.isDirectory(repoPath)) {
                throw new IllegalArgumentException("Repository not found: " + repoName);
            }
            addLog(logLines, ref, startedAt, "Cloning repository from: " + repoPath);
            try (Git git = Git.cloneRepository()
                    .setURI(repoPath.toUri().toString())
                    .setDirectory(workDir.toFile())
                    .call()) {
                addLog(logLines, ref, startedAt, "Repository cloned successfully.");
            }

            // Step 2: check package.json
            Path packageJson = workDir.resolve("package.json");
            if (!Files.exists(packageJson)) {
                throw new IllegalStateException("No package.json found in repository. Ensure the repository contains a Node.js project.");
            }
            addLog(logLines, ref, startedAt, "Found package.json.");

            // Step 3: npm install
            addLog(logLines, ref, startedAt, "Running: npm install");
            runProcess(workDir, logLines, ref, startedAt, "npm", "install", "--no-audit", "--no-fund");

            // Step 4: npm run build
            addLog(logLines, ref, startedAt, "Running: npm run build");
            runProcess(workDir, logLines, ref, startedAt, "npm", "run", "build");

            // Step 5: find output directory
            Path outputDir = findOutputDir(workDir);
            if (outputDir == null) {
                throw new IllegalStateException(
                        "Build output directory not found. Expected one of: " + BUILD_OUTPUT_DIRS +
                        ". Ensure your build script outputs to one of these directories.");
            }
            addLog(logLines, ref, startedAt, "Build output found at: " + workDir.relativize(outputDir));

            // Step 6: deploy to storage as a Pages site
            String siteName = repoName.toLowerCase().replaceAll("[^a-z0-9._-]", "-");
            addLog(logLines, ref, startedAt, "Deploying site '" + siteName + "' to Pages storage...");
            deployToPages(siteName, outputDir, logLines, ref, startedAt);

            String completedAt = Instant.now().toString();
            addLog(logLines, ref, startedAt, "=== Build completed successfully. Site: " + siteName + " ===");
            ref.set(new TurGitBuildStatus(
                    TurGitBuildState.COMPLETED, List.copyOf(logLines), siteName, null, startedAt, completedAt));

        } catch (Exception e) {
            log.error("[GitPipeline] Build failed for repository: {}", repoName, e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            logLines.add("=== Build FAILED: " + msg + " ===");
            ref.set(new TurGitBuildStatus(
                    TurGitBuildState.FAILED, List.copyOf(logLines), null, msg, startedAt, Instant.now().toString()));
        } finally {
            if (workDir != null) {
                deleteQuietly(workDir);
            }
        }
    }

    private void runProcess(Path workDir, List<String> logLines,
                            AtomicReference<TurGitBuildStatus> ref,
                            String startedAt,
                            String... command) throws IOException, InterruptedException {
        // On Windows, npm is a .cmd file; resolve using shell
        String[] fullCommand = resolveCommand(command);
        ProcessBuilder pb = new ProcessBuilder(fullCommand)
                .directory(workDir.toFile())
                .redirectErrorStream(true);
        pb.environment().put("CI", "false"); // prevent treating warnings as errors in CRA

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                addLog(logLines, ref, startedAt, line);
            }
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Command '" + String.join(" ", command) + "' exited with code " + exitCode);
        }
    }

    private String[] resolveCommand(String[] command) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win") && command[0].equals("npm")) {
            // On Windows, npm is npm.cmd
            String[] winCmd = new String[command.length + 2];
            winCmd[0] = "cmd";
            winCmd[1] = "/c";
            System.arraycopy(command, 0, winCmd, 2, command.length);
            return winCmd;
        }
        return command;
    }

    private Path findOutputDir(Path workDir) {
        for (String candidate : BUILD_OUTPUT_DIRS) {
            Path p = workDir.resolve(candidate);
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        return null;
    }

    private void deployToPages(String siteName, Path outputDir,
                               List<String> logLines,
                               AtomicReference<TurGitBuildStatus> ref,
                               String startedAt) throws IOException {
        String prefix = PAGES_PREFIX + siteName + "/";
        storageService.deleteObjectsWithPrefix(prefix);

        try (Stream<Path> walk = Files.walk(outputDir)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                String relative = outputDir.relativize(file).toString().replace('\\', '/');
                // Skip hidden files
                if (relative.startsWith(".") || relative.contains("/.")) return;
                String objectName = prefix + relative;
                String contentType = TurStorageContentTypes.guessContentType(file.toString());
                try {
                    byte[] data = Files.readAllBytes(file);
                    storageService.uploadStream(objectName, new ByteArrayInputStream(data), data.length, contentType);
                } catch (IOException e) {
                    log.warn("[GitPipeline] Failed to upload file: {}", relative, e);
                }
            });
        }
        addLog(logLines, ref, startedAt, "Deployed " + siteName + " to /pages/" + siteName);
    }

    private void addLog(List<String> logLines, AtomicReference<TurGitBuildStatus> ref,
                        String startedAt, String line) {
        log.info("[GitPipeline] {}", line);
        logLines.add(line);
        TurGitBuildStatus current = ref.get();
        ref.set(new TurGitBuildStatus(
                TurGitBuildState.RUNNING, List.copyOf(logLines),
                current.siteName(), current.errorMessage(), startedAt, null));
    }

    private void deleteQuietly(Path dir) {
        try (Stream<Path> walk = Files.walk(dir).sorted(Comparator.reverseOrder())) {
            walk.forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) { /* best-effort */ }
            });
        } catch (IOException ignored) { /* best-effort */ }
    }

    /** Creates a ZIP of a directory in memory (not stored to disk). */
    @SuppressWarnings("unused")
    private byte[] zipDirectory(Path sourceDir) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos);
             Stream<Path> walk = Files.walk(sourceDir)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                String entryName = sourceDir.relativize(file).toString().replace('\\', '/');
                try {
                    zos.putNextEntry(new ZipEntry(entryName));
                    Files.copy(file, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    log.warn("[GitPipeline] Failed to add to ZIP: {}", entryName, e);
                }
            });
        }
        return bos.toByteArray();
    }
}
