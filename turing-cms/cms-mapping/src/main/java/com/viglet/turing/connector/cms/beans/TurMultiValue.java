/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.connector.cms.beans;

import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

@Setter
@Getter
public class TurMultiValue extends ArrayList<String> {

	@Serial
	private static final long serialVersionUID = 1L;
	public static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
	public static final String UTC = "UTC";
	private final boolean override;

	public TurMultiValue() {
		this.override = false;
	}
    public TurMultiValue(boolean override) {
        this.override = override;
    }

	public static <T> TurMultiValue singleItem(T value, boolean override) {
		return switch (value) {
			case String string -> singleItem(string, override);
			case Boolean bool -> singleItem(bool, override);
			case Date date -> singleItem(date, override);
			case null, default -> value != null ? singleItem(value.toString(), override) : null;
		};
	}

    public static <T> TurMultiValue singleItem(T value) {
		return singleItem(value, false);
	}
	public static TurMultiValue singleItem(String text, boolean override) {
		TurMultiValue turMultiValue = new TurMultiValue(override);
		turMultiValue.add(text);
		return turMultiValue;
	}

	public static TurMultiValue singleItem(String text) {
		return singleItem(text, false);
	}

	public static TurMultiValue singleItem(boolean bool, boolean override) {
		TurMultiValue turMultiValue = new TurMultiValue(override);
		turMultiValue.add(bool? "true": "false");
		return turMultiValue;
	}


	public static TurMultiValue singleItem(boolean bool) {
		return singleItem(bool, false);
	}

	public static TurMultiValue singleItem(Date date, boolean override) {
		if (date == null) {
			return new TurMultiValue(override);
		}
		else {
			TimeZone tz = TimeZone.getTimeZone(UTC);
			DateFormat df = new SimpleDateFormat(DATE_FORMAT);
			df.setTimeZone(tz);
			return singleItem(df.format(date), override);
		}
	}
	public static TurMultiValue singleItem(Date date) {
		return singleItem(date, false);
	}
	public static TurMultiValue fromList(List<String> list) {
		TurMultiValue turMultiValue = new TurMultiValue();
		turMultiValue.addAll(list);
		return turMultiValue;
	}

	public static TurMultiValue empty() {
        return new TurMultiValue();
	}
}
