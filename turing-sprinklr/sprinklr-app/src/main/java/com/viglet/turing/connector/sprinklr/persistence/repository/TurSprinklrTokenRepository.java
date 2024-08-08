package com.viglet.turing.connector.sprinklr.persistence.repository;


import com.viglet.turing.connector.sprinklr.persistence.model.TurSprinklrToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TurSprinklrTokenRepository extends JpaRepository<TurSprinklrToken, String> {
}
