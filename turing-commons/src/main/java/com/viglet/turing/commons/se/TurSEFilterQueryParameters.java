package com.viglet.turing.commons.se;

import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

@Getter
@Setter
public class TurSEFilterQueryParameters implements Serializable {
    private List<String> fq;
    private List<String> and;
    private List<String> or;
    private TurSNFilterQueryOperator operator;

    public TurSEFilterQueryParameters(List<String> fq, List<String> and, List<String> or,
                                      TurSNFilterQueryOperator operator) {
        this.fq = fq != null ? fq : Collections.emptyList();
        this.and = and != null ? and : Collections.emptyList();
        this.or = or != null ? or : Collections.emptyList();
        this.operator = operator;
    }

    @Override
    public String toString() {
        return "TurSEFilterQueryParameters{" +
                "fq=" + fq +
                ", and=" + and +
                ", or=" + or +
                ", operator=" + operator +
                '}';
    }
}
