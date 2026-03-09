package com.viglet.turing.persistence.repository.sn.locale;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.repository.RepositoryContractAssertions;

class TurSNSiteLocaleRepositoryTest {
    @Test
    void shouldFollowJpaRepositoryContract() {
        RepositoryContractAssertions.assertJpaRepositoryContract(TurSNSiteLocaleRepository.class);
    }

    @Test
    void shouldExposeCacheKeyConstants() {
        assertThat(TurSNSiteLocaleRepository.FIND_BY_TUR_SN_SITE_AND_LANGUAGE)
                .isEqualTo("turSNSiteLocaleFindByTurSNSiteAndLanguage");
        assertThat(TurSNSiteLocaleRepository.EXISTS_BY_TUR_SN_SITE_AND_LANGUAGE)
                .isEqualTo("turSNSiteLocaleExistsByTurSNSiteAndLanguage");
        assertThat(TurSNSiteLocaleRepository.FIND_BY_TUR_SN_SITE_SORT)
                .isEqualTo("turSNSiteLocaleFindByTurSNSiteSort");
        assertThat(TurSNSiteLocaleRepository.FIND_BY_TUR_SN_SITE)
                .isEqualTo("turSNSiteLocaleFindByTurSNSite");
    }
}
