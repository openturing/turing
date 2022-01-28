package com.viglet.turing.wem.beans;

import java.util.ArrayList;

public class TurMultiValue extends ArrayList<String> {

	private static final long serialVersionUID = 1L;

	public static TurMultiValue singleItem(String text) {
		TurMultiValue turMultiValue = new TurMultiValue();
		turMultiValue.add(text);
		return turMultiValue;
	}

}
