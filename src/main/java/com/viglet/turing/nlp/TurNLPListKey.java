package com.viglet.turing.nlp;

import java.util.ArrayList;

public class TurNLPListKey<T> {
	private ArrayList<T> list;
	StringBuffer hashCodeSb = new StringBuffer();

	public ArrayList<T> getList() {
		return list;
	}

	public void setList(ArrayList<T> list) {
		this.list = list;
	}

	@SuppressWarnings("unchecked")
	public TurNLPListKey(ArrayList<T> list) {
		this.list = (ArrayList<T>) list.clone();
		for (int i = 0; i < this.list.size(); i++) {
			T item = this.list.get(i);
			hashCodeSb.append(item);
			hashCodeSb.append(",");
		}
	}

	@Override
	public int hashCode() {
		return hashCodeSb.toString().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		StringBuffer hashCodeObjectSb = new StringBuffer();
		TurNLPListKey<T> objList = (TurNLPListKey<T>) obj;
		for (int i = 0; i < objList.list.size(); i++) {
			T item = objList.list.get(i);
			hashCodeObjectSb.append(item);
			hashCodeObjectSb.append(",");
		}
		return hashCodeSb.toString().equals(hashCodeObjectSb.toString());
	}

	@Override
	public String toString() {
		return hashCodeSb.toString();
	}

}