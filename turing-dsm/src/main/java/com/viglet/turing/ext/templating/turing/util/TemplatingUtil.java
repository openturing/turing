package com.viglet.turing.ext.templating.turing.util;

import java.lang.reflect.Field;

public class TemplatingUtil {

	public TemplatingUtil() {
		// TODO Auto-generated constructor stub
	}

	public static String getToString(Object obj)
	  {
	    return getToString(obj, obj != null ? obj.getClass() : null);
	  }
	  
	  public static String getToString(Object obj, Class clazz)
	  {
	    if ((obj == null) || (clazz == null)) {
	      return "";
	    }
	    StringBuffer sb = new StringBuffer();
	    try
	    {
	      Field[] fields = clazz.getDeclaredFields();
	      for (Field element : fields)
	      {
	        element.setAccessible(true);
	        sb.append(element.getName() + " = " + element.get(obj) + ",");
	      }
	      if (clazz.getSuperclass() != Object.class) {
	        sb.append(getToString(obj, clazz.getSuperclass()));
	      }
	      if (sb.indexOf(",") != -1) {
	        sb.deleteCharAt(sb.length() - 1);
	      }
	    }
	    catch (Exception e) {}
	    return sb.toString();
	  }
	
}
