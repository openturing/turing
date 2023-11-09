package com.viglet.turing.connector.aem.indexer;

import com.beust.jcommander.IStringConverter;

public class TurAemModeEnumConverter  implements IStringConverter<TurAemMode> {
    public TurAemMode convert(String name) {
        return TurAemMode.getModeByName(name);
    }
}
