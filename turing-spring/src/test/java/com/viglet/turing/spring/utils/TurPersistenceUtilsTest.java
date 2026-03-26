package com.viglet.turing.spring.utils;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TurPersistenceUtils.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurPersistenceUtilsTest {

    // --- orderByNameIgnoreCase ---

    @Test
    void orderByNameIgnoreCaseShouldReturnNonNullSort() {
        Sort sort = TurPersistenceUtils.orderByNameIgnoreCase();
        assertNotNull(sort);
    }

    @Test
    void orderByNameIgnoreCaseShouldSortByNameProperty() {
        Sort sort = TurPersistenceUtils.orderByNameIgnoreCase();
        Sort.Order order = sort.iterator().next();
        assertEquals("name", order.getProperty());
    }

    @Test
    void orderByNameIgnoreCaseShouldBeAscending() {
        Sort sort = TurPersistenceUtils.orderByNameIgnoreCase();
        Sort.Order order = sort.iterator().next();
        assertTrue(order.isAscending());
    }

    @Test
    void orderByNameIgnoreCaseShouldIgnoreCase() {
        Sort sort = TurPersistenceUtils.orderByNameIgnoreCase();
        Sort.Order order = sort.iterator().next();
        assertTrue(order.isIgnoreCase());
    }

    @Test
    void orderByNameIgnoreCaseShouldHaveSingleOrder() {
        Sort sort = TurPersistenceUtils.orderByNameIgnoreCase();
        long count = sort.stream().count();
        assertEquals(1, count);
    }

    // --- orderByTitleIgnoreCase ---

    @Test
    void orderByTitleIgnoreCaseShouldReturnNonNullSort() {
        Sort sort = TurPersistenceUtils.orderByTitleIgnoreCase();
        assertNotNull(sort);
    }

    @Test
    void orderByTitleIgnoreCaseShouldSortByTitleProperty() {
        Sort sort = TurPersistenceUtils.orderByTitleIgnoreCase();
        Sort.Order order = sort.iterator().next();
        assertEquals("title", order.getProperty());
    }

    @Test
    void orderByTitleIgnoreCaseShouldBeAscending() {
        Sort sort = TurPersistenceUtils.orderByTitleIgnoreCase();
        Sort.Order order = sort.iterator().next();
        assertTrue(order.isAscending());
    }

    @Test
    void orderByTitleIgnoreCaseShouldIgnoreCase() {
        Sort sort = TurPersistenceUtils.orderByTitleIgnoreCase();
        Sort.Order order = sort.iterator().next();
        assertTrue(order.isIgnoreCase());
    }

    @Test
    void orderByTitleIgnoreCaseShouldHaveSingleOrder() {
        Sort sort = TurPersistenceUtils.orderByTitleIgnoreCase();
        long count = sort.stream().count();
        assertEquals(1, count);
    }

    // --- orderByLanguageIgnoreCase ---

    @Test
    void orderByLanguageIgnoreCaseShouldReturnNonNullSort() {
        Sort sort = TurPersistenceUtils.orderByLanguageIgnoreCase();
        assertNotNull(sort);
    }

    @Test
    void orderByLanguageIgnoreCaseShouldSortByLanguageProperty() {
        Sort sort = TurPersistenceUtils.orderByLanguageIgnoreCase();
        Sort.Order order = sort.iterator().next();
        assertEquals("language", order.getProperty());
    }

    @Test
    void orderByLanguageIgnoreCaseShouldBeAscending() {
        Sort sort = TurPersistenceUtils.orderByLanguageIgnoreCase();
        Sort.Order order = sort.iterator().next();
        assertTrue(order.isAscending());
    }

    @Test
    void orderByLanguageIgnoreCaseShouldIgnoreCase() {
        Sort sort = TurPersistenceUtils.orderByLanguageIgnoreCase();
        Sort.Order order = sort.iterator().next();
        assertTrue(order.isIgnoreCase());
    }

    @Test
    void orderByLanguageIgnoreCaseShouldHaveSingleOrder() {
        Sort sort = TurPersistenceUtils.orderByLanguageIgnoreCase();
        long count = sort.stream().count();
        assertEquals(1, count);
    }

    // --- Utility class enforcement ---

    @Test
    void constructorShouldThrowIllegalStateException() {
        Constructor<TurPersistenceUtils> constructor;
        try {
            constructor = TurPersistenceUtils.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                    constructor::newInstance);
            assertInstanceOf(IllegalStateException.class, ex.getCause());
            assertEquals("Utility class", ex.getCause().getMessage());
        } catch (NoSuchMethodException e) {
            fail("Expected private constructor to exist");
        }
    }

    // --- Consistency checks ---

    @Test
    void allSortMethodsShouldNotBeDescending() {
        assertFalse(TurPersistenceUtils.orderByNameIgnoreCase().iterator().next().isDescending());
        assertFalse(TurPersistenceUtils.orderByTitleIgnoreCase().iterator().next().isDescending());
        assertFalse(TurPersistenceUtils.orderByLanguageIgnoreCase().iterator().next().isDescending());
    }

    @Test
    void allSortMethodsShouldReturnDifferentProperties() {
        String nameProp = TurPersistenceUtils.orderByNameIgnoreCase().iterator().next().getProperty();
        String titleProp = TurPersistenceUtils.orderByTitleIgnoreCase().iterator().next().getProperty();
        String langProp = TurPersistenceUtils.orderByLanguageIgnoreCase().iterator().next().getProperty();

        assertNotEquals(nameProp, titleProp);
        assertNotEquals(nameProp, langProp);
        assertNotEquals(titleProp, langProp);
    }

    @Test
    void multipleCallsShouldReturnEqualSorts() {
        assertEquals(TurPersistenceUtils.orderByNameIgnoreCase(),
                TurPersistenceUtils.orderByNameIgnoreCase());
        assertEquals(TurPersistenceUtils.orderByTitleIgnoreCase(),
                TurPersistenceUtils.orderByTitleIgnoreCase());
        assertEquals(TurPersistenceUtils.orderByLanguageIgnoreCase(),
                TurPersistenceUtils.orderByLanguageIgnoreCase());
    }

    @Test
    void sortMethodsShouldNotBeUnsorted() {
        assertNotEquals(Sort.unsorted(), TurPersistenceUtils.orderByNameIgnoreCase());
        assertNotEquals(Sort.unsorted(), TurPersistenceUtils.orderByTitleIgnoreCase());
        assertNotEquals(Sort.unsorted(), TurPersistenceUtils.orderByLanguageIgnoreCase());
    }
}
