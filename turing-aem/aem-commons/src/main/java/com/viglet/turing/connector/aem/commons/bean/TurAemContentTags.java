package com.viglet.turing.connector.aem.commons.bean;

import lombok.Getter;
import lombok.Setter;

import java.util.Collection;
import java.util.HashSet;

@Getter
@Setter
public class TurAemContentTags {
    private Collection<TurAemContentTag> tags = new HashSet<>();
}
