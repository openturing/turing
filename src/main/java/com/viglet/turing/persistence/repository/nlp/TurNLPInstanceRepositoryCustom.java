package com.viglet.turing.persistence.repository.nlp;

import com.viglet.turing.persistence.model.nlp.TurNLPInstance;

public interface TurNLPInstanceRepositoryCustom {

	TurNLPInstance saveAndAssocEntity(TurNLPInstance turNLPInstance);
}
