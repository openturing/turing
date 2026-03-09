package com.viglet.turing.api.sn.graphql;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;

import graphql.schema.idl.RuntimeWiring;

class GraphQLConfigTest {

    @Test
    void testConfigure() {
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        GraphQLConfig config = new GraphQLConfig(siteRepository, fieldExtRepository);
        RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();

        assertDoesNotThrow(() -> config.configure(builder));
        assertNotNull(config.dynamicSearchDocumentFieldsCustomizer());
    }
}
