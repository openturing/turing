package com.viglet.turing.connector.sprinklr.plugins;

import com.viglet.turing.sprinklr.plugins.KeyValueTransformer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class KeyValueTransformerTest {

    @Autowired
    private KeyValueTransformer transformer;

    @Test
    void loadMapping() {
        // Caminho para o arquivo de teste (ajustar para o local correto)
        String testFile = "src/test/resources/test-mapping.txt";

        // Chama o método loadMapping
        transformer.loadMapping(testFile);

        // Verifica se o mapeamento foi carregado
        Map<String, String> mapping = transformer.getMapping(testFile);
        assertNotNull(mapping, "Mapping should not be null after loading");
        assertFalse(mapping.isEmpty(), "Mapping should not be empty after loading");

        // Verifica se uma chave específica foi carregada corretamente
        assertEquals("ação", mapping.get("424123421312"), "Value for key '424123421312' should be 'ação'");
    }

    @Test
    void getMapping() {
        // Caminho para o arquivo de teste (ajustar para o local correto)
        String testFile = "src/test/resources/test-mapping.txt";

        // Certifique-se de que o mapeamento foi carregado
        transformer.loadMapping(testFile);

        // Recupera o mapeamento e verifica
        Map<String, String> mapping = transformer.getMapping(testFile);
        assertNotNull(mapping, "Mapping should not be null");
        // Há duas linhas com a mesma chave
        assertEquals(2, mapping.size(), "Mapping should have 2 entries");

        // Verifica se uma chave específica está presente
        assertTrue(mapping.containsKey("235521957944"), "Mapping should contain key '235521957944'");
    }

    @Test
    void transform() {
        // Caminho para o arquivo de teste (ajustar para o local correto)
        String testFile = "src/test/resources/test-mapping.txt";

        // Certifique-se de que o mapeamento foi carregado
        transformer.loadMapping(testFile);

        // Testa a transformação
        String transformedValue = transformer.transform("424123421312", testFile);
        assertEquals("ação", transformedValue, "Transformed value should be 'ação'");

        // Testa uma chave inexistente
        String missingValue = transformer.transform("999999999999", testFile);
    }

    @Test
    void loadMappingTransfromLoadSameMapTransform(){
        // Caminho para o arquivo de teste (ajustar para o local correto)
        String testFile = "src/test/resources/test-mapping.txt";

        // Chama o método loadMapping
        transformer.loadMapping(testFile);

        var transformedValue = transformer.transform("424123421312", testFile);
        assertEquals("ação", transformedValue, "Transformed value should be 'ação'");
        transformer.loadMapping(testFile);
        transformedValue = transformer.transform("235521957944", testFile);
        assertEquals("aventura", transformedValue, "Transformed value should be 'aventura'");



    }

    @Test
    void getName() {
        String name = transformer.getName();
        assertNotNull(name);
        assertEquals("Key Value Transformer Plugin", transformer.getName());

    }

    @Test
    void getDescription() {
        String description = transformer.getDescription();
        assertNotNull(description);
        assertEquals("Transforms a value into another value based on a key-value mapping file", transformer.getDescription());
    }
}