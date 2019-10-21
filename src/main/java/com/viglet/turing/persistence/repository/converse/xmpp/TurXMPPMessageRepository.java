package com.viglet.turing.persistence.repository.converse.xmpp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.converse.xmpp.TurXMPPMessage;

public interface TurXMPPMessageRepository
		extends JpaRepository<TurXMPPMessage, Long>, JpaSpecificationExecutor<TurXMPPMessage> {
	@Modifying
	@Query("delete from TurXMPPMessage m where m.id = ?1")
	void delete(Long id);
}
