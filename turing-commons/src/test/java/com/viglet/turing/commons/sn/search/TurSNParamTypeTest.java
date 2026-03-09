package com.viglet.turing.commons.sn.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

class TurSNParamTypeTest {

    @Test
    void shouldExposeKnownParameterNames() {
        assertThat(TurSNParamType.QUERY).isEqualTo("q");
        assertThat(TurSNParamType.PAGE).isEqualTo("p");
        assertThat(TurSNParamType.FILTER_QUERIES_DEFAULT).isEqualTo("fq[]");
        assertThat(TurSNParamType.FILTER_QUERIES_AND).isEqualTo("fq.and[]");
        assertThat(TurSNParamType.FILTER_QUERIES_OR).isEqualTo("fq.or[]");
        assertThat(TurSNParamType.SORT).isEqualTo("sort");
        assertThat(TurSNParamType.ROWS).isEqualTo("rows");
        assertThat(TurSNParamType.LOCALE).isEqualTo("_setlocale");
        assertThat(TurSNParamType.AUTO_CORRECTION_DISABLED).isEqualTo("nfpr");
    }

    @Test
    void utilityConstructorShouldThrow() throws Exception {
        Constructor<TurSNParamType> constructor = TurSNParamType.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        try {
            constructor.newInstance();
            fail("Expected InvocationTargetException");
        } catch (InvocationTargetException ex) {
            assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
            assertThat(ex.getCause().getMessage()).isEqualTo("Parameter Type class");
        }
    }
}
