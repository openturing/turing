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
    @Column(name = "id", nullable = false)
    protected String id;
    @Column(length = 50)
    protected String name;
    @Column
    protected String description;

}
