package com.viglet.turing.plugins.se;

import com.viglet.turing.persistence.model.se.TurSEInstance;

public interface TurSeContext {
  public Object getConnection(TurSEInstance turSEInstance);
}
