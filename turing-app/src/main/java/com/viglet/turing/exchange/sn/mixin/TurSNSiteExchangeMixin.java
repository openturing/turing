package com.viglet.turing.exchange.sn.mixin;

import com.fasterxml.jackson.annotation.JsonIgnore;

public abstract class TurSNSiteExchangeMixin {
    @JsonIgnore
    abstract Object getTurSNSite();
}
