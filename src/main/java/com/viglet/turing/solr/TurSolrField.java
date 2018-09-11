package com.viglet.turing.solr;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

import org.springframework.stereotype.Component;

import com.viglet.turing.se.field.TurSEFieldType;

@Component
public class TurSolrField {

	public Integer convertFieldToInt(Object attrValue) {
		if (attrValue instanceof String) {
			return Integer.parseInt((String) attrValue);
		} else if (attrValue instanceof ArrayList) {
			ArrayList<?> arrAttValue = (ArrayList<?>) attrValue;
			if (arrAttValue.get(0) instanceof Long) {
				return ((Long) arrAttValue.get(0)).intValue();
			} else if (arrAttValue.get(0) instanceof String) {
				return Integer.parseInt((String) arrAttValue.get(0));
			} else {
				return (Integer) arrAttValue.get(0);
			}

		} else if (attrValue instanceof Long) {
			return ((Long) attrValue).intValue();
		} else {
			return (int) attrValue;
		}
	}

	public Long convertFieldToLong(Object attrValue) {
		if (attrValue instanceof String) {
			return Long.parseLong((String) attrValue);
		} else if (attrValue instanceof ArrayList) {
			ArrayList<?> arrAttValue = (ArrayList<?>) attrValue;
			if (arrAttValue.get(0) instanceof String) {
				return Long.parseLong((String) arrAttValue.get(0));
			} else {
				return (Long) arrAttValue.get(0);
			}
		} else {
			return (long) attrValue;
		}
	}

	public String convertFieldToString(Object attrValue) {
		if (attrValue instanceof String) {
			return (String) attrValue;
		} else if (attrValue instanceof ArrayList) {
			ArrayList<?> arrAttValue = (ArrayList<?>) attrValue;
			if (arrAttValue.get(0) instanceof String) {
				return (String) arrAttValue.get(0);
			} else if (arrAttValue.get(0) instanceof Long) {
				return ((Long) arrAttValue.get(0)).toString();
			} else if (arrAttValue.get(0) instanceof Date) {
				return ((Date) arrAttValue.get(0)).toString();
			} else {
				return (String) arrAttValue.get(0);
			}
		} else if (attrValue instanceof Long) {
			return ((Long) attrValue).toString();
		} else if (attrValue instanceof Object[]) {
			Object[] arrAttrValue = (Object[]) attrValue;
			if (arrAttrValue[0] instanceof String) {
				return (String) arrAttrValue[0];
			} else if (arrAttrValue[0] instanceof Long) {
				return ((Long) arrAttrValue[0]).toString();
			} else if (arrAttrValue[0] instanceof Date) {
				return ((Date) arrAttrValue[0]).toString();
			} else {
				return (String) arrAttrValue[0];
			}
		} else {
			return (String) attrValue;
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public Object[] convertFieldToArray(Object attrValue) {
		if (attrValue instanceof String) {
			String[] array = { (String) attrValue };
			return array;
		} else if (attrValue instanceof Long) {
			Long[] array = { (Long) attrValue };
			return array;
		} else if (attrValue instanceof ArrayList) {
			return ((ArrayList) attrValue).toArray(new Object[0]);
		} else {
			return (Object[]) attrValue;
		}
	}

	public String convertFieldToDate(Object attrValue) {
		if (attrValue instanceof ArrayList) {
			ArrayList<?> arrAttValue = (ArrayList<?>) attrValue;
			if (arrAttValue.get(0) instanceof String) {
				return (String) arrAttValue.get(0);
			} else if (arrAttValue.get(0) instanceof Long) {
				return ((Long) arrAttValue.get(0)).toString();
			} else if (arrAttValue.get(0) instanceof Date) {
				return ((Date) arrAttValue.get(0)).toString();
			} else {
				return (String) arrAttValue.get(0);
			}
		} else if (attrValue instanceof Long) {
			return ((Long) attrValue).toString();
		} else if (attrValue instanceof Date) {
			TimeZone tz = TimeZone.getTimeZone("UTC");
			DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
			df.setTimeZone(tz);
			return df.format((Date) attrValue);
		} else {
			return (String) attrValue;
		}
	}

	public boolean convertFieldToBoolean(Object attrValue) {
		if (attrValue instanceof String) {
			return Boolean.parseBoolean((String) attrValue);
		} else if (attrValue instanceof ArrayList) {
			ArrayList<?> arrAttValue = (ArrayList<?>) attrValue;
			if (arrAttValue.get(0) instanceof String) {
				return Boolean.parseBoolean((String) arrAttValue.get(0));
			} else if (arrAttValue.get(0) instanceof Long) {
				if (((Long) arrAttValue.get(0)) > 0) {
					return true;
				} else {
					return false;
				}
			} else {
				return (boolean) attrValue;
			}
		} else if (attrValue instanceof Long) {
			if (((Long) attrValue) > 0) {
				return true;
			} else {
				return false;
			}
		} else {
			return (boolean) attrValue;
		}
	}

	public Object convertField(TurSEFieldType finalType, Object attrValue) {
		switch (finalType) {
		case INT:
			return this.convertFieldToInt(attrValue);
		case LONG:
			return this.convertFieldToLong(attrValue);
		case STRING:
			return this.convertFieldToString(attrValue);
		//case ARRAY:
		//	return this.convertFieldToArray(attrValue);
		case DATE:
			return this.convertFieldToDate(attrValue);
		case BOOL:
			return this.convertFieldToBoolean(attrValue);
		default:
			return attrValue;
		}
	}
}
