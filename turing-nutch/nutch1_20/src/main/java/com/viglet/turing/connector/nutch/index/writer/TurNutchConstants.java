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

package com.viglet.turing.connector.nutch.index.writer;

public class TurNutchConstants {
    private TurNutchConstants() {
        throw new IllegalStateException("Nutch Constants class");
    }

    public static final String COMMIT_SIZE = "commitSize";
    public static final String WEIGHT_FIELD = "weight.field";
    public static final String SERVER_URL = "url";
    public static final String SITE = "site";
    public static final String USE_AUTH = "auth";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";
    public static final String LOCALE_PROPERTY = "locale";
}
