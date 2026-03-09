package com.viglet.turing.api.system;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.viglet.turing.system.TurGlobalDecimalSeparator;

class TurGlobalSettingsBeanTest {

    @Test
    void shouldCreateUsingBuilder() {
        TurGlobalSettingsBean bean = TurGlobalSettingsBean.builder()
                .decimalSeparator(TurGlobalDecimalSeparator.COMMA)
                .build();

        assertThat(bean.getDecimalSeparator()).isEqualTo(TurGlobalDecimalSeparator.COMMA);
    }

    @Test
    void shouldCreateUsingAllArgsConstructor() {
        TurGlobalSettingsBean bean = new TurGlobalSettingsBean(TurGlobalDecimalSeparator.DOT, null);

        assertThat(bean.getDecimalSeparator()).isEqualTo(TurGlobalDecimalSeparator.DOT);
    }

    @Test
    void shouldSupportNoArgsConstructorAndSetter() {
        TurGlobalSettingsBean bean = new TurGlobalSettingsBean();

        assertThat(bean.getDecimalSeparator()).isNull();

        bean.setDecimalSeparator(TurGlobalDecimalSeparator.COMMA);

        assertThat(bean.getDecimalSeparator()).isEqualTo(TurGlobalDecimalSeparator.COMMA);
    }
}
