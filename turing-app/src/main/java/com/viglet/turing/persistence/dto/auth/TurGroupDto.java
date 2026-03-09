package com.viglet.turing.persistence.dto.auth;

import com.viglet.turing.persistence.model.auth.TurGroup;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TurGroupDto extends TurGroup {
}