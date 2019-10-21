
package com.viglet.turing.persistence.repository.converse.xmpp;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.viglet.turing.persistence.model.converse.xmpp.TurXMPPUser;

public interface TurXMPPUserRepository extends JpaRepository<TurXMPPUser, Long> {

	
	Optional<TurXMPPUser> findById(Long id);

	TurXMPPUser findByUsername(String username);
	
	@Modifying
	@Query("delete from TurXMPPUser u where u.id = ?1")
	void delete(Long id);
}
