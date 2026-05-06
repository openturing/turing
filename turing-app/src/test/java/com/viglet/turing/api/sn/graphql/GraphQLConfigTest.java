package com.viglet.turing.api.sn.graphql;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import com.viglet.turing.domain.sn.TurSNSiteRepositoryPort;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;

import graphql.schema.idl.RuntimeWiring;

class GraphQLConfigTest {

    @Test
    void testConfigure() {
        TurSNSiteRepositoryPort siteRepositoryPort = mock(TurSNSiteRepositoryPort.class);
        TurSNSiteFieldExtRepository fieldExtRepository = mock(TurSNSiteFieldExtRepository.class);
        GraphQLConfig config = new GraphQLConfig(siteRepositoryPort, fieldExtRepository);
        RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();

        assertDoesNotThrow(() -> config.configure(builder));
        assertNotNull(config.dynamicSearchDocumentFieldsCustomizer());
    }
}
