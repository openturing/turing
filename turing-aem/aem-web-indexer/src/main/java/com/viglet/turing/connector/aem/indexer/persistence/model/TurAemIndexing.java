package com.viglet.turing.connector.aem.indexer.persistence.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.Locale;

@RequiredArgsConstructor
@Accessors(chain = true)
@Setter
@Getter
@Entity
@Table(name="aem_indexing", uniqueConstraints={@UniqueConstraint(columnNames={"id"})})
public class TurAemIndexing implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id", nullable=false, unique=true, length=11)
    private int id;
    @Column
    private String aemId;
    @Column
    private Date date;
    @Column
    private String indexGroup;
    @Column
    private String deltaId;
    @Column
    private Locale locale;
    @Column
    private boolean once;
}