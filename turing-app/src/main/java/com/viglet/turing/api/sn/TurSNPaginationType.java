package com.viglet.turing.api.sn;

public enum TurSNPaginationType {

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
