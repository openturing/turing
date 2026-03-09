package com.viglet.turing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.CharacterEncodingFilter;

import tools.jackson.databind.JacksonModule;

class TuringESTest {

    @Test
    void testFilterRegistrationBean() {
        TuringES turingES = new TuringES();
        FilterRegistrationBean<CharacterEncodingFilter> registrationBean = turingES.filterRegistrationBean();

        assertNotNull(registrationBean);
        assertTrue(registrationBean.getFilter() instanceof CharacterEncodingFilter);

        CharacterEncodingFilter filter = registrationBean.getFilter();
        assertEquals(TuringES.UTF_8, filter.getEncoding());
    }

    @Test
    void testHibernate7Module() {
        TuringES turingES = new TuringES();
        JacksonModule module = turingES.hibernate7Module();

        assertNotNull(module);
    }
}
