package com.viglet.turing.api.system;

import com.viglet.turing.system.TurGlobalDecimalSeparator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TurGlobalSettingsBean {
    private TurGlobalDecimalSeparator decimalSeparator;
    private String pythonExecutable;
}
