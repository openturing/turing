package com.viglet.turing.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.data.jpa.repository.JpaRepository;

public final class RepositoryContractAssertions {

    private RepositoryContractAssertions() {
    }

    public static void assertJpaRepositoryContract(Class<?> repositoryType) {
        assertThat(repositoryType.isInterface()).isTrue();
        assertThat(JpaRepository.class.isAssignableFrom(repositoryType)).isTrue();
    }
}
