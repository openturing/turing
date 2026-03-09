package com.viglet.turing.exchange.sn.mixin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIgnore;

class TurSNSiteExchangeMixinTest {

    @Test
    void shouldMarkGetTurSNSiteAsJsonIgnored() throws Exception {
        Method method = TurSNSiteExchangeMixin.class.getDeclaredMethod("getTurSNSite");

        assertThat(method.getAnnotation(JsonIgnore.class)).isNotNull();
    }
}
