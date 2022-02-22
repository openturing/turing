package com.viglet.turing.client.sn.facet;

public class TurSNFacetField {

	private String label;

	private String name;

	private String description;

	private boolean multiValued;

	private String type;

	private TurSNFacetFieldValueList values;

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public TurSNFacetFieldValueList getValues() {
		return values;
	}

	public void setValues(TurSNFacetFieldValueList values) {
		this.values = values;
	}

	public int getValueCount() {
		return values.getTurSNFacetFieldValues().size();
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public boolean isMultiValued() {
		return multiValued;
	}

	public void setMultiValued(boolean multiValue) {
		this.multiValued = multiValue;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

}
