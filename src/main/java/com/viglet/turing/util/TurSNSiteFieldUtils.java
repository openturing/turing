package com.viglet.turing.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldRepository;

@Component
public class TurSNSiteFieldUtils {
	
	@Autowired
	TurSNSiteFieldRepository turSNSiteFieldRepository;
	public Map<String, TurSNSiteField> toMap(TurSNSite turSNSite) {
		Set<TurSNSiteField> turSNSiteFields = turSNSite.getTurSNSiteFields();
		Map<String, TurSNSiteField> turSNSiteFieldsMap = new HashMap<String, TurSNSiteField>();
		for (TurSNSiteField turSNSiteField : turSNSiteFields)
			turSNSiteFieldsMap.put(turSNSiteField.getName(), turSNSiteField);

		return turSNSiteFieldsMap;

	}
}
