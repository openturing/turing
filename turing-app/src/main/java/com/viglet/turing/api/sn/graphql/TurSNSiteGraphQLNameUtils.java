package com.viglet.turing.api.sn.graphql;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.StringUtils;

public final class TurSNSiteGraphQLNameUtils {

    public static final String UNKNOWN_ENUM_VALUE = "UNKNOWN";

    private TurSNSiteGraphQLNameUtils() {
    }

    public static Map<String, String> buildEnumToSiteNameMap(List<String> siteNames) {
        Map<String, String> enumToSiteName = new LinkedHashMap<>();
        Set<String> usedEnumValues = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        if (siteNames == null || siteNames.isEmpty()) {
            return enumToSiteName;
        }

        siteNames.stream().filter(StringUtils::isNotBlank).map(String::trim)
                .forEach(siteName -> {
                    String baseEnumValue = toGraphQLEnumValue(siteName);
                    String candidate = baseEnumValue;
                    int suffix = 2;

                    while (UNKNOWN_ENUM_VALUE.equalsIgnoreCase(candidate)
                            || usedEnumValues.contains(candidate)) {
                        candidate = baseEnumValue + "_" + suffix;
                        suffix++;
                    }

                    usedEnumValues.add(candidate);
                    enumToSiteName.put(candidate, siteName);
                });

        return enumToSiteName;
    }

    public static String resolveGraphQLSiteArgument(String siteArgument,
            Map<String, String> enumToSiteName) {
        if (StringUtils.isBlank(siteArgument)) {
            return siteArgument;
        }
        if (enumToSiteName == null || enumToSiteName.isEmpty()) {
            return siteArgument;
        }
        if (enumToSiteName.containsKey(siteArgument)) {
            return enumToSiteName.get(siteArgument);
        }
        return enumToSiteName.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(siteArgument))
                .map(java.util.Map.Entry::getValue)
                .findFirst()
                .orElse(siteArgument);
    }

    /**
     * Converts a field name to a valid GraphQL field name by replacing hyphens
     * with double underscores ({@code __}).
     *
     * @param fieldName the original field name (e.g. "my-field")
     * @return a GraphQL-safe field name (e.g. "my__field"), or the original if no hyphens
     */
    public static String toGraphQLFieldName(String fieldName) {
        if (fieldName == null || !fieldName.contains("-")) {
            return fieldName;
        }
        return fieldName.replace("-", "__");
    }

    public static String toGraphQLEnumValue(String siteName) {
        String upper = siteName.toUpperCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(upper.length());
        for (int i = 0; i < upper.length(); i++) {
            char c = upper.charAt(i);
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                sb.append(c);
            } else if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '_') {
                sb.append('_');
            }
        }
        // Strip trailing underscore
        int end = sb.length();
        while (end > 0 && sb.charAt(end - 1) == '_') {
            end--;
        }
        String normalized = sb.substring(0, end);

        if (StringUtils.isBlank(normalized)) {
            normalized = "SITE";
        }

        if (!Character.isLetter(normalized.charAt(0)) && normalized.charAt(0) != '_') {
            normalized = "SITE_" + normalized;
        }

        if (normalized.startsWith("__")) {
            normalized = "SITE" + normalized;
        }

        return normalized;
    }
}
