package com.viglet.turing.persistence.model;

import com.viglet.turing.spring.security.TurAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Getter
@Setter
@MappedSuperclass
public abstract class TurObject extends TurAuditable<String> {
    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    protected String id;
    @Column(nullable = false, length = 50)
    protected String name;
    @Column(nullable = false, length = 255)
    protected String description;

}
