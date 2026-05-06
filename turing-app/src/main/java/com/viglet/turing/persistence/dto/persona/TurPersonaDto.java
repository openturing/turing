/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.dto.persona;

import com.viglet.turing.persistence.model.persona.TurPersona;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Wire DTO for {@link TurPersona}. Same shape as the entity — Mapstruct
 * round-trips it via {@code TurPersonaMapper}.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TurPersonaDto extends TurPersona {
}
