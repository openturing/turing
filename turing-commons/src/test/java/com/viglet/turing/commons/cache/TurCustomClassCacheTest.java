package com.viglet.turing.commons.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class TurCustomClassCacheTest {

    @Test
    void shouldInstantiateAndCacheClassInstance() {
        Optional<Object> first = TurCustomClassCache.getCustomClassMap(SampleCacheClass.class.getName());
        Optional<Object> second = TurCustomClassCache.getCustomClassMap(SampleCacheClass.class.getName());

        assertThat(first).isPresent();
        assertThat(second).isPresent();
        assertThat(first.orElseThrow()).isSameAs(second.orElseThrow());
    }

    @Test
    void shouldReturnEmptyForUnknownClass() {
        Optional<Object> value = TurCustomClassCache.getCustomClassMap("no.such.Class");
        assertThat(value).isEmpty();
    }

    @Test
    void utilityConstructorShouldThrow() throws Exception {
        Constructor<TurCustomClassCache> constructor = TurCustomClassCache.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        try {
            constructor.newInstance();
            fail("Expected InvocationTargetException");
        } catch (InvocationTargetException ex) {
            assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
        }
    }

    public static class SampleCacheClass {
    }
}
