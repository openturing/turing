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
package com.viglet.turing.solr;

import com.viglet.turing.commons.se.field.TurSEFieldType;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

public class TurSolrField {

	public static Integer convertFieldToInt(Object attrValue) {
		if (attrValue instanceof String string) {
			return Integer.parseInt(string);
		} else if (attrValue instanceof ArrayList<?> arrAttValue) {
			if (arrAttValue.get(0) instanceof Long longValue) {
				return longValue.intValue();
			} else if (arrAttValue.get(0) instanceof String stringValue) {
				return Integer.parseInt(stringValue);
			} else {
				return (Integer) arrAttValue.get(0);
			}

		} else if (attrValue instanceof Long longValue) {
			return longValue.intValue();
		} else {
			return (int) attrValue;
		}
	}

	public static Long convertFieldToLong(Object attrValue) {
		if (attrValue instanceof String stringValue) {
			return Long.parseLong(stringValue);
		} else if (attrValue instanceof ArrayList<?> arrAttValue) {
			if (arrAttValue.get(0) instanceof String stringValue) {
				return Long.parseLong(stringValue);
			} else {
				return (Long) arrAttValue.get(0);
			}
		} else {
			return (long) attrValue;
		}
	}

	public static String convertFieldToString(Object attrValue) {
		if (attrValue instanceof String) {
			return (String) attrValue;
		} else if (attrValue instanceof ArrayList<?> arrayListValue) {
			return arrayListToString(arrayListValue);
		} else if (attrValue instanceof Long longValue) {
			return longToString(longValue);
		} else if (attrValue instanceof Object[] objectValue) {
			return objectArrayToString(objectValue);
		} else if (attrValue instanceof Date dateValue) {
			SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
			simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
			return simpleDateFormat.format(dateValue);
		} else {
			return attrValue.toString();
		}
	}

	private static String objectArrayToString(Object[] arrAttrValue) {
		if (arrAttrValue[0] instanceof String stringValue) {
			return stringValue;
		} else if (arrAttrValue[0] instanceof Long longValue) {
			return longValue.toString();
		} else if (arrAttrValue[0] instanceof Date dateValue) {
			return dateValue.toString();
		} else {
			return (String) arrAttrValue[0];
		}
	}

	private static String arrayListToString(ArrayList<?> arrAttValue) {
		if (arrAttValue.get(0) instanceof String stringValue) {
			return stringValue;
		} else if (arrAttValue.get(0) instanceof Long longValue) {
			return longValue.toString();
		} else if (arrAttValue.get(0) instanceof Date dateValue) {
			return dateValue.toString();
		} else {
			return (String) arrAttValue.get(0);
		}
	}

	public static Object[] convertFieldToArray(Object attrValue) {
		if (attrValue instanceof String stringValue) {
			return new String[] { stringValue };
		} else if (attrValue instanceof Long longValue) {
			return new Long[] { longValue };
		} else if (attrValue instanceof ArrayList<?> arrayListValue) {
			return arrayListValue.toArray(new Object[0]);
		} else {
			return (Object[]) attrValue;
		}
	}

	public static String convertFieldToDate(Object attrValue) {
		if (attrValue instanceof ArrayList<?> arrayListValue) {
			return arrayListToString(arrayListValue);
		} else if (attrValue instanceof Long longValue) {
			return longToString(longValue);
		} else if (attrValue instanceof Date dateValue) {
			return dateToString(dateValue);
		} else {
			return (String) attrValue;
		}
	}

	private static String longToString(Long longValue) {
		return longValue.toString();
	}

	private static String dateToString(Date dateValue) {
		TimeZone tz = TimeZone.getTimeZone("UTC");
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		df.setTimeZone(tz);
		return df.format(dateValue);
	}

	public static boolean convertFieldToBoolean(Object attrValue) {
		if (attrValue instanceof String) {
			return Boolean.parseBoolean((String) attrValue);
		} else if (attrValue instanceof ArrayList<?> arrAttValue) {
			if (arrAttValue.get(0) instanceof String) {
				return Boolean.parseBoolean((String) arrAttValue.get(0));
			} else if (arrAttValue.get(0) instanceof Long) {
				return (((Long) arrAttValue.get(0)) > 0);
			} else {
				return (boolean) attrValue;
			}
		} else if (attrValue instanceof Long) {
			return (((Long) attrValue) > 0);
		} else {
			return (boolean) attrValue;
		}
	}

	public static Object convertField(TurSEFieldType finalType, Object attrValue) {
        return switch (finalType) {
            case INT -> convertFieldToInt(attrValue);
            case LONG -> convertFieldToLong(attrValue);
            case STRING -> convertFieldToString(attrValue);
            case DATE -> convertFieldToDate(attrValue);
            case BOOL -> convertFieldToBoolean(attrValue);
            default -> attrValue;
        };
	}
}
