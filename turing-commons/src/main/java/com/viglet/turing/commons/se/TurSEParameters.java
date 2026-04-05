package com.viglet.turing.commons.se;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.viglet.turing.commons.sn.bean.TurSNFilterParams;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class TurSEParameters implements Serializable {
    private String query;
    private TurSNFilterParams turSNFilterParams;
    private List<String> boostQueries;
    private List<String> fieldList;
    private Integer currentPage;
    private String sort;
    private Integer rows;
    private String group;
    private Integer autoCorrectionDisabled;
    private TurSNSitePostParamsBean turSNSitePostParamsBean;
    private Set<String> allowedFacets;
    private Set<String> addedFacets;
    private Set<String> removedFacets;

    public TurSEParameters(TurSNSearchParams turSNSearchParams) {
        this(turSNSearchParams, null);
    }

    public TurSEParameters(TurSNSearchParams turSNSearchParams,
                           TurSNSitePostParamsBean gTurSNSitePostParamsBean) {
        super();
        this.query = turSNSearchParams.getQ();
        this.turSNFilterParams = TurSNFilterParams.builder()
                .defaultValues(turSNSearchParams.getFq())
                .and(turSNSearchParams.getFqAnd()).or(turSNSearchParams.getFqOr())
                .operator(turSNSearchParams.getFqOp())
                .itemOperator(turSNSearchParams.getFqiOp()).build();
        this.currentPage = turSNSearchParams.getP();
        this.sort = turSNSearchParams.getSort();
        this.rows = turSNSearchParams.getRows();
        this.group = turSNSearchParams.getGroup();
        this.autoCorrectionDisabled = turSNSearchParams.getNfpr();
        this.fieldList = turSNSearchParams.getFl();
        this.turSNSitePostParamsBean = gTurSNSitePostParamsBean;
        overrideFromPost(gTurSNSitePostParamsBean);
    }

    private void overrideFromPost(TurSNSitePostParamsBean postParamsBean) {
        if (postParamsBean == null) {
            return;
        }
        setSort(Optional.ofNullable(postParamsBean.getSort()).orElse(sort));
        setRows(Optional.ofNullable(postParamsBean.getRows()).orElse(rows));
        setGroup(Optional.ofNullable(postParamsBean.getGroup()).orElse(group));
        setCurrentPage(Optional.ofNullable(postParamsBean.getPage()).orElse(currentPage));
        setQuery(Optional.ofNullable(postParamsBean.getQuery()).orElse(query));
        setFieldList(Optional.ofNullable(postParamsBean.getFieldList()).orElse(fieldList));

        if (turSNFilterParams == null) {
            return;
        }
        turSNFilterParams.setDefaultValues(
                Optional.ofNullable(postParamsBean.getFq()).orElse(
                        turSNFilterParams.getDefaultValues()));
        turSNFilterParams.setAnd(Optional.ofNullable(postParamsBean.getFqAnd())
                .orElse(turSNFilterParams.getAnd()));
        turSNFilterParams.setOr(Optional.ofNullable(postParamsBean.getFqOr())
                .orElse(turSNFilterParams.getOr()));
        turSNFilterParams.setOperator(
                Optional.ofNullable(postParamsBean.getFqOperator())
                        .orElse(turSNFilterParams.getOperator()));
    }
}
