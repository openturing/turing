package com.viglet.turing.persistence.dto.llm;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TurLLMInstanceDto extends TurLLMInstance {
}