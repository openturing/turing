package com.viglet.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;

@Component
public class TurSNSiteFieldUtils {
	public Map<String, TurSNSiteField> toMap(TurSNSite turSNSite) {
		List<TurSNSiteField> turSNSiteFields = turSNSite.getTurSNSiteFields();
		Map<String, TurSNSiteField> turSNSiteFieldsMap = new HashMap<String, TurSNSiteField>();
		for (TurSNSiteField turSNSiteField : turSNSiteFields)
			turSNSiteFieldsMap.put(turSNSiteField.getName(), turSNSiteField);

		return turSNSiteFieldsMap;

	}
}
