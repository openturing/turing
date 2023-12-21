package com.viglet.turing.persistence.utils;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Sort;

public class TurPesistenceUtils {
    @NotNull
    public static Sort orderByNameIgnoreCase() {
        return Sort.by(Sort.Order.asc("name").ignoreCase());
    }
    public static Sort orderByTitleIgnoreCase() {
        return Sort.by(Sort.Order.asc("title").ignoreCase());
    }
    public static Sort orderByLanguageIgnoreCase() {
        return Sort.by(Sort.Order.asc("language").ignoreCase());
    }
}
