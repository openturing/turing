package com.viglet.turing.system.scheduling;

import com.hazelcast.core.HazelcastInstance;

import net.javacrumbs.shedlock.provider.hazelcast4.HazelcastLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import net.javacrumbs.shedlock.core.LockProvider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cluster-wide coordination for {@code @Scheduled} tasks. ShedLock backed by
 * Hazelcast ensures that, when multiple Turing nodes share a Hazelcast cluster,
 * each scheduled task fires on exactly one node per execution.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
public class TurSchedulerLockConfig {

    @Bean
    LockProvider lockProvider(HazelcastInstance hazelcastInstance) {
        return new HazelcastLockProvider(hazelcastInstance);
    }
}
