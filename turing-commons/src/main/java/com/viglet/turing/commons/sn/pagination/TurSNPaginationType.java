package com.viglet.turing.commons.sn.pagination;

import java.io.Serializable;

public enum TurSNPaginationType implements Serializable {

	FIRST {
		@Override
		public String toString() {
			return "FIRST";
		}
	},
	LAST {
		@Override
		public String toString() {
			return "LAST";
		}
	},
	PREVIOUS {
		@Override
		public String toString() {
			return "PREVIOUS";
		}
	},
	NEXT {
		@Override
		public String toString() {
			return "NEXT";
		}
	},
	CURRENT {
		@Override
		public String toString() {
			return "CURRENT";
		}
	},
	PAGE {
		@Override
		public String toString() {
			return "PAGE";
		}
	}

}
