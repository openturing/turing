/*
 * Copyright (C) 2016-2022 the original author or authors. 
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
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
