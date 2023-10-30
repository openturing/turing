package com.viglet.turing.commons.sn.search;

public enum TurSNFilterQueryOperator {
    AND {
        @Override
        public String toString() {
            return "AND";
        }
    },
    OR {
        @Override
        public String toString() {
            return "OR";
        }
    },
}
