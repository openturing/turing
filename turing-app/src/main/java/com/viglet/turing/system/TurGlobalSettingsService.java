package com.viglet.turing.system;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.viglet.turing.onstartup.system.TurConfigVarOnStartup;
import com.viglet.turing.persistence.model.system.TurConfigVar;
import com.viglet.turing.persistence.repository.system.TurConfigVarRepository;

@Service
public class TurGlobalSettingsService {
    private final TurConfigVarRepository turConfigVarRepository;

    public TurGlobalSettingsService(TurConfigVarRepository turConfigVarRepository) {
        this.turConfigVarRepository = turConfigVarRepository;
    }

    public TurGlobalDecimalSeparator getDecimalSeparator() {
        String value = turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR)
                .map(TurConfigVar::getValue)
                .orElse(TurConfigVarOnStartup.DECIMAL_SEPARATOR_DEFAULT_VALUE);

        try {
            return TurGlobalDecimalSeparator
                    .valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return TurGlobalDecimalSeparator
                    .valueOf(TurConfigVarOnStartup.DECIMAL_SEPARATOR_DEFAULT_VALUE);
        }
    }

    public TurGlobalDecimalSeparator updateDecimalSeparator(TurGlobalDecimalSeparator decimalSeparator) {
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.DECIMAL_SEPARATOR)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.DECIMAL_SEPARATOR);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(decimalSeparator.name());
        turConfigVarRepository.save(configVar);
        return decimalSeparator;
    }

    public String getPythonExecutable() {
        return turConfigVarRepository.findById(TurConfigVarOnStartup.PYTHON_EXECUTABLE)
                .map(TurConfigVar::getValue)
                .orElse("");
    }

    public String updatePythonExecutable(String pythonExecutable) {
        String value = pythonExecutable == null ? "" : pythonExecutable.trim();
        TurConfigVar configVar = turConfigVarRepository.findById(TurConfigVarOnStartup.PYTHON_EXECUTABLE)
                .orElseGet(TurConfigVar::new);
        configVar.setId(TurConfigVarOnStartup.PYTHON_EXECUTABLE);
        configVar.setPath(TurConfigVarOnStartup.GLOBAL_PATH);
        configVar.setValue(value);
        turConfigVarRepository.save(configVar);
        return value;
    }
}
