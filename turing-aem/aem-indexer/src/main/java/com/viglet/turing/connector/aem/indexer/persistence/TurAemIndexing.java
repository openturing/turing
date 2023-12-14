package com.viglet.turing.connector.aem.indexer.persistence;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.persistence.*;
import java.util.Date;

@RequiredArgsConstructor
@Accessors(chain = true)
@Setter
@Getter
@Entity
@Table(name="aem_indexing",
        uniqueConstraints={@UniqueConstraint(columnNames={"id"})})
public class TurAemIndexing {

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
    private String locale;
}