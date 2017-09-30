package com.viglet.turing.api;

import org.springframework.stereotype.Component;

@Component

public class TurAPIBean {

	String product;

	public String getProduct() {
		return product;
	}

	public void setProduct(String product) {
		this.product = product;
	}
	
}
