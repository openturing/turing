package com.viglet.turing.persistence.model.intent;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import com.viglet.turing.persistence.utils.TurAssignableUuidGenerator;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Represents an Intent — a category of suggested prompts in the chat.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.17
 */
@Getter
@Setter
@Entity
@Table(name = "intent")
public class TurIntent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @TurAssignableUuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private String id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(length = 150)
    private String icon;

    @Column(nullable = false)
    private int enabled;

    @Column(name = "sortOrder")
    private int sortOrder;

    @OneToMany(mappedBy = "turIntent", orphanRemoval = true,
            fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @OnDelete(action = OnDeleteAction.CASCADE)
    @OrderBy("sortOrder ASC")
    private Set<TurIntentAction> actions = new HashSet<>();

    public void setActions(Set<TurIntentAction> actions) {
        this.actions.clear();
        if (actions != null) {
            for (TurIntentAction action : actions) {
                action.setTurIntent(this);
                this.actions.add(action);
            }
        }
    }
}
