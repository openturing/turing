/*
 * Copyright (C) 2016-2022 the original author or authors. 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.viglet.turing.plugins.nlp.gcp.response;

/**
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
public enum TurNLPGCPEntityTypeResponse {

	UNKNOWN {
		@Override
		public String toString() {
			return "UNKNOWN";
		}
	},
	PERSON {
		@Override
		public String toString() {
			return "PERSON";
		}
	},
	LOCATION {
		@Override
		public String toString() {
			return "LOCATION";
		}
	},

	ORGANIZATION {
		@Override
		public String toString() {
			return "ORGANIZATION";
		}
	},
	EVENT {
		@Override
		public String toString() {
			return "EVENT";
		}
	},
	WORK_OF_ART {
		@Override
		public String toString() {
			return "WORK_OF_ART";
		}
	},
	CONSUMER_GOOD {
		@Override
		public String toString() {
			return "CONSUMER_GOOD";
		}
	},
	OTHER {
		@Override
		public String toString() {
			return "OTHER";
		}
	},
	PHONE_NUMBER {
		@Override
		public String toString() {
			return "PHONE_NUMBER";
		}
	},
	NUMBER {
		@Override
		public String toString() {
			return "NUMBER";
		}
	},
	PRICE {
		@Override
		public String toString() {
			return "PRICE";
		}
	},
	DATE {
		@Override
		public String toString() {
			return "DATE";
		}
	},
	ADDRESS {
		@Override
		public String toString() {
			return "ADDRESS";
		}
	}
}
