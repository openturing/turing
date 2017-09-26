package com.viglet.turing.persistence.bean.storage;

import org.springframework.stereotype.Component;

@Component

public class TurDataGroupSentenceBean {

	int id;
	String sentence;
	int turData;
	int turDataGroup;
	int turMLCategory;
	
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public String getSentence() {
		return sentence;
	}
	public void setSentence(String sentence) {
		this.sentence = sentence;
	}
	public int getTurData() {
		return turData;
	}
	public void setTurData(int turData) {
		this.turData = turData;
	}
	public int getTurDataGroup() {
		return turDataGroup;
	}
	public void setTurDataGroup(int turDataGroup) {
		this.turDataGroup = turDataGroup;
	}
	public int getTurMLCategory() {
		return turMLCategory;
	}
	public void setTurMLCategory(int turMLCategory) {
		this.turMLCategory = turMLCategory;
	}

}
