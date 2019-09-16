/*
 * Copyright (C) 2016-2019 the original author or authors. 
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

package com.viglet.turing.persistence.model.converse;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

import javax.persistence.*;

import org.hibernate.annotations.Cascade;
import org.hibernate.annotations.CascadeType;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * The persistent class for the TurConverseIntent database table.
 * 
 */
@Entity
@Table(name = "turConverseIntent")
@NamedQuery(name = "TurConverseIntent.findAll", query = "SELECT ci FROM TurConverseIntent ci")
public class TurConverseIntent implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GenericGenerator(name = "UUID", strategy = "com.viglet.turing.jpa.TurUUIDGenerator")
	@GeneratedValue(generator = "UUID")
	@Column(name = "id", updatable = false, nullable = false)
	private String id;

	@OneToMany(mappedBy = "intent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConverseContext> contextInputs = new HashSet<>();

	@OneToMany(mappedBy = "intent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConverseContext> contextOutputs;

	@OneToMany(mappedBy = "intent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConverseEvent> events;

	@OneToMany(mappedBy = "intent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConversePhrase> phrases;

	@OneToMany(mappedBy = "intent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConverseAction> actions;

	@OneToMany(mappedBy = "intent", orphanRemoval = true, fetch = FetchType.LAZY)
	@Cascade({ CascadeType.ALL })
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Set<TurConverseResponse> responses;	

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Set<TurConverseContext> getContextInputs() {
		return this.contextInputs;
	}

	public void setContextInputs(Set<TurConverseContext> contextInputs) {
		this.contextInputs.clear();
		if (contextInputs != null) {
			this.contextInputs.addAll(contextInputs);
		}
	}

	public Set<TurConverseContext> getContextOutputs() {
		return this.contextOutputs;
	}

	public void setContextOutputs(Set<TurConverseContext> contextOutputs) {
		this.contextOutputs.clear();
		if (contextOutputs != null) {
			this.contextOutputs.addAll(contextOutputs);
		}
	}

	public Set<TurConverseEvent> getEvents() {
		return this.events;
	}

	public void setEvents(Set<TurConverseEvent> events) {
		this.events.clear();
		if (events != null) {
			this.events.addAll(events);
		}
	}
	
	public Set<TurConversePhrase> getPhrases() {
		return this.phrases;
	}

	public void setPhrases(Set<TurConversePhrase> phrases) {
		this.phrases.clear();
		if (phrases != null) {
			this.phrases.addAll(phrases);
		}
	}
	
	public Set<TurConverseAction> getActions() {
		return this.actions;
	}

	public void setActions(Set<TurConverseAction> actions) {
		this.actions.clear();
		if (actions != null) {
			this.actions.addAll(actions);
		}
	}
	
	public Set<TurConverseResponse> getResponses() {
		return this.responses;
	}

	public void setResponses(Set<TurConverseResponse> responses) {
		this.responses.clear();
		if (responses != null) {
			this.responses.addAll(responses);
		}
	}
}