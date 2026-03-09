package com.viglet.turing.spring.security.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class ITurAuthenticationFacadeTest {

    @Test
    void shouldBeInterfaceWithGetAuthenticationMethod() throws Exception {
        Method getAuthentication = ITurAuthenticationFacade.class.getDeclaredMethod("getAuthentication");

        assertThat(ITurAuthenticationFacade.class.isInterface()).isTrue();
        assertThat(getAuthentication.getReturnType()).isEqualTo(Authentication.class);
        assertThat(getAuthentication.getParameterCount()).isZero();
    }
}
