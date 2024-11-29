package com.viglet.turing.commons.se;

import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.Optional;

@Getter
@Setter
public class TurSEParameters implements Serializable {
    private String query;
    private TurSEFilterQueryParameters filterQueries;
    private List<String> boostQueries;
    private Integer currentPage;
    private String sort;
    private Integer rows;
    private String group;
    private Integer autoCorrectionDisabled;
    private TurSNSitePostParamsBean turSNSitePostParamsBean;

    public TurSEParameters(String query, TurSEFilterQueryParameters filterQueries,
                           Integer currentPage, String sort, Integer rows,
                           String group, Integer autoCorrectionDisabled) {
        this(query, filterQueries, currentPage, sort, rows, group, autoCorrectionDisabled, null);
    }

    public TurSEParameters(String gQuery, TurSEFilterQueryParameters gFilterQueries,
                           Integer gCurrentPage, String gSort, Integer gRows,
                           String gGroup, Integer gAutoCorrectionDisabled,
                           TurSNSitePostParamsBean gTurSNSitePostParamsBean) {
        super();
        this.query = gQuery;
        this.filterQueries = gFilterQueries;
        this.currentPage = gCurrentPage;
        this.sort = gSort;
        this.rows = gRows;
        this.group = gGroup;
        this.autoCorrectionDisabled = gAutoCorrectionDisabled;
        this.turSNSitePostParamsBean = gTurSNSitePostParamsBean;
        overrideFromPost(gTurSNSitePostParamsBean);
    }

    private void overrideFromPost(TurSNSitePostParamsBean gTurSNSitePostParamsBean) {
        Optional.ofNullable(gTurSNSitePostParamsBean).ifPresent(postParams -> {
            setSort(Optional.ofNullable(postParams.getSort()).orElse(getSort()));
            setRows(Optional.ofNullable(postParams.getRows()).orElse(getRows()));
            setGroup(Optional.ofNullable(postParams.getGroup()).orElse(getGroup()));
            setCurrentPage(Optional.ofNullable(postParams.getPage()).orElse(getCurrentPage()));
            setQuery(Optional.ofNullable(postParams.getQuery()).orElse(getQuery()));
            getFilterQueries().setFq(Optional.ofNullable(postParams.getFq()).orElse(getFilterQueries().getFq()));
            getFilterQueries().setAnd(Optional.ofNullable(postParams.getFqAnd()).orElse(getFilterQueries().getAnd()));
            getFilterQueries().setOr(Optional.ofNullable(postParams.getFqOr()).orElse(getFilterQueries().getOr()));
            getFilterQueries().setOperator(Optional.ofNullable(postParams.getFqOperator())
                    .orElse(getFilterQueries().getOperator()));
        });
    }

    @Override
    public String toString() {
        return "TurSEParameters{" +
                "query='" + query + '\'' +
                ", filterQueries=" + filterQueries +
                ", boostQueries=" + boostQueries +
                ", currentPage=" + currentPage +
                ", sort='" + sort + '\'' +
                ", rows=" + rows +
                ", group='" + group + '\'' +
                ", autoCorrectionDisabled=" + autoCorrectionDisabled +
                '}';
    }
}
