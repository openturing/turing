package com.viglet.turing.api.git;

import com.viglet.turing.service.git.TurGitServerService;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Registers the JGit {@link GitServlet} as a Spring-managed servlet so that
 * git-http-backend operations (clone, fetch, push) are available under
 * {@code /git/*} when the git server is enabled.
 *
 * <p>Authentication is handled by Spring Security upstream; only authenticated
 * users can reach this servlet.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Configuration
public class TurGitServletConfig {

    private static final Logger log = LoggerFactory.getLogger(TurGitServletConfig.class);

    @Bean
    ServletRegistrationBean<GitServlet> gitServlet(TurGitServerService gitServerService) {
        GitServlet servlet = new GitServlet();

        RepositoryResolver<HttpServletRequest> resolver = (req, name) -> {
            if (!gitServerService.isEnabled()) {
                throw new ServiceNotEnabledException();
            }
            // Strip optional leading slash from name
            String repoName = name.startsWith("/") ? name.substring(1) : name;
            // Accept with or without .git suffix
            String dirName = repoName.endsWith(".git") ? repoName : repoName + ".git";
            File repoDir = gitServerService.getReposBasePath().resolve(dirName).toFile();
            if (!repoDir.isDirectory()) {
                log.warn("Git repository not found: {}", repoDir);
                throw new ServiceNotAuthorizedException();
            }
            try {
                Repository repo = new FileRepositoryBuilder()
                        .setGitDir(repoDir)
                        .setBare()
                        .build();
                repo.incrementOpen();
                return repo;
            } catch (Exception e) {
                log.error("Failed to open git repository: {}", repoDir, e);
                throw new ServiceNotAuthorizedException();
            }
        };

        servlet.setRepositoryResolver(resolver);
        // Allow upload-pack (clone / fetch) for authenticated users
        servlet.setUploadPackFactory((req, repo) -> new UploadPack(repo));
        // Allow receive-pack (push) for authenticated users
        servlet.setReceivePackFactory((req, repo) -> new ReceivePack(repo));

        ServletRegistrationBean<GitServlet> registration = new ServletRegistrationBean<>(servlet, "/git/*");
        registration.setName("gitServlet");
        registration.setEnabled(gitServerService.isEnabled());
        return registration;
    }
}
