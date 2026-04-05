/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.service.git;

import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Browses bare Git repositories using JGit: list branches, list file tree,
 * and read file content.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Service
public class TurGitBrowseService {

    private static final Logger log = LoggerFactory.getLogger(TurGitBrowseService.class);

    private final TurGitServerService gitServerService;

    public TurGitBrowseService(TurGitServerService gitServerService) {
        this.gitServerService = gitServerService;
    }

    // ── DTOs ──

    public record GitBranch(String name, boolean isDefault) {}

    public record GitTreeEntry(String name, String path, String type, long size) {}

    // ── Branches ──

    public List<GitBranch> listBranches(String repoName) {
        Path repoPath = gitServerService.getRepoPath(repoName);
        List<GitBranch> branches = new ArrayList<>();
        try (Repository repo = openBareRepo(repoPath)) {
            Ref head = repo.exactRef("HEAD");
            String headTarget = head != null && head.getTarget() != null
                    ? head.getTarget().getName() : null;
            Collection<Ref> refs = repo.getRefDatabase().getRefsByPrefix("refs/heads/");
            for (Ref ref : refs) {
                String branchName = ref.getName().replace("refs/heads/", "");
                boolean isDefault = ref.getName().equals(headTarget);
                branches.add(new GitBranch(branchName, isDefault));
            }
        } catch (IOException e) {
            log.error("Failed to list branches for repo '{}'", repoName, e);
        }
        return branches;
    }

    // ── Tree ──

    public List<GitTreeEntry> listTree(String repoName, String ref, String treePath) {
        Path repoPath = gitServerService.getRepoPath(repoName);
        List<GitTreeEntry> entries = new ArrayList<>();
        try (Repository repo = openBareRepo(repoPath)) {
            Optional<RevCommit> commit = resolveCommit(repo, ref);
            if (commit.isEmpty()) return entries;

            try (TreeWalk treeWalk = new TreeWalk(repo)) {
                treeWalk.addTree(commit.get().getTree());
                treeWalk.setRecursive(false);

                // Navigate to subdirectory if path specified
                if (treePath != null && !treePath.isEmpty()) {
                    treeWalk.setRecursive(false);
                    boolean found = false;
                    // Walk to the target path
                    String[] parts = treePath.split("/");
                    for (String part : parts) {
                        while (treeWalk.next()) {
                            if (treeWalk.getNameString().equals(part)
                                    && treeWalk.isSubtree()) {
                                treeWalk.enterSubtree();
                                found = true;
                                break;
                            }
                        }
                        if (!found) return entries;
                        found = false;
                    }
                }

                while (treeWalk.next()) {
                    String name = treeWalk.getNameString();
                    String entryPath = treeWalk.getPathString();
                    if (treeWalk.isSubtree()) {
                        entries.add(new GitTreeEntry(name, entryPath, "tree", 0));
                    } else {
                        ObjectLoader loader = repo.open(treeWalk.getObjectId(0));
                        entries.add(new GitTreeEntry(name, entryPath, "blob", loader.getSize()));
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to list tree for repo '{}' ref '{}' path '{}'", repoName, ref, treePath, e);
        }
        // Sort: directories first, then files, alphabetically
        entries.sort((a, b) -> {
            if (a.type().equals(b.type())) return a.name().compareToIgnoreCase(b.name());
            return "tree".equals(a.type()) ? -1 : 1;
        });
        return entries;
    }

    // ── File content ──

    public Optional<byte[]> readFile(String repoName, String ref, String filePath) {
        Path repoPath = gitServerService.getRepoPath(repoName);
        try (Repository repo = openBareRepo(repoPath)) {
            Optional<RevCommit> commit = resolveCommit(repo, ref);
            if (commit.isEmpty()) return Optional.empty();

            try (TreeWalk treeWalk = TreeWalk.forPath(repo, filePath, commit.get().getTree())) {
                if (treeWalk == null) return Optional.empty();
                ObjectLoader loader = repo.open(treeWalk.getObjectId(0));
                return Optional.of(loader.getBytes());
            }
        } catch (IOException e) {
            log.error("Failed to read file '{}' from repo '{}' ref '{}'", filePath, repoName, ref, e);
        }
        return Optional.empty();
    }

    // ── Helpers ──

    private Repository openBareRepo(Path repoPath) throws IOException {
        return new FileRepositoryBuilder()
                .setGitDir(repoPath.toFile())
                .setBare()
                .build();
    }

    private Optional<RevCommit> resolveCommit(Repository repo, String refName) throws IOException {
        ObjectId objectId;
        if (refName == null || refName.isBlank()) {
            objectId = repo.resolve("HEAD");
        } else {
            objectId = repo.resolve(refName);
        }
        if (objectId == null) return Optional.empty();
        try (RevWalk revWalk = new RevWalk(repo)) {
            return Optional.of(revWalk.parseCommit(objectId));
        }
    }
}
