package com.viglet.turing.connector.sprinklr.persistence.repository;


import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurSprinklrSourceRepository  extends JpaRepository<TurSprinklrSource, String> {
}
