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
package com.viglet.turing.commons.exception;

import com.viglet.core.commons.exception.VigletRuntimeException;

/**
 * Turing's unchecked exception. Thin {@code Tur*} subclass of the neutral
 * {@link VigletRuntimeException} (Block Q / T375) so the base lives in {@code
 * viglet-core-commons} while every {@code throw}/{@code catch} site is
 * unchanged.
 */
public class TurRuntimeException extends VigletRuntimeException {
	public TurRuntimeException(String string) {
		super(string);
	}
	public TurRuntimeException(Throwable throwable) {super(throwable);}

    public TurRuntimeException(String s, Exception e) {
		super(s, e);
    }
}
