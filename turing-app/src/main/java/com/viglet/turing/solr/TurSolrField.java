/*
 * Copyright (C) 2016-2019 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.solr;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

import com.viglet.turing.commons.se.field.TurSEFieldType;

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

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static Object[] convertFieldToArray(Object attrValue) {
		if (attrValue instanceof String stringValue) {
			return new String[] { stringValue };
		} else if (attrValue instanceof Long longValue) {
			return new Long[] { longValue };
		} else if (attrValue instanceof ArrayList arrayListValue) {
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
		} else if (attrValue instanceof ArrayList) {
			ArrayList<?> arrAttValue = (ArrayList<?>) attrValue;
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
		switch (finalType) {
		case INT:
			return convertFieldToInt(attrValue);
		case LONG:
			return convertFieldToLong(attrValue);
		case STRING:
			return convertFieldToString(attrValue);
		case DATE:
			return convertFieldToDate(attrValue);
		case BOOL:
			return convertFieldToBoolean(attrValue);
		default:
			return attrValue;
		}
	}
}
