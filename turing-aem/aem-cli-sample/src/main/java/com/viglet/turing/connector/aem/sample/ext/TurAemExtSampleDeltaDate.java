/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.connector.aem.sample.ext;

import com.viglet.turing.connector.aem.commons.TurAemObject;
import com.viglet.turing.connector.aem.commons.context.TurAemSourceContext;
import com.viglet.turing.connector.aem.commons.ext.TurAemExtDeltaDateInterface;


import java.util.Calendar;
import java.util.Date;
import java.util.Optional;

public class TurAemExtSampleDeltaDate implements TurAemExtDeltaDateInterface {
    @Override
    public Date consume(TurAemObject aemObject, TurAemSourceContext turAemSourceContext) {
        return Optional.ofNullable(aemObject.getLastModified())
                .map(Calendar::getTime).orElse(null);
    }
}
