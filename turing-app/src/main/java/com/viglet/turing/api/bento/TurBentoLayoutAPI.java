/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.bento;

import java.util.List;

import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.bento.TurBentoLayoutEntry;
import com.viglet.turing.bento.TurBentoLayoutResponse;
import com.viglet.turing.bento.TurBentoLayoutService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * T574 / §XXXI.11 — read/write the per-user and admin-global layout of a Bento
 * list surface. The resolver cascade (user override → global template → default)
 * lives in {@link TurBentoLayoutService}; this controller only exposes it and
 * gates the global write to admins.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@RestController
@RequestMapping("/api/v2/bento-layout")
@Tag(name = "Bento Layout", description = "Customizable Bento list layout (emphasis + order)")
public class TurBentoLayoutAPI {

    private final TurBentoLayoutService bentoLayoutService;

    public TurBentoLayoutAPI(TurBentoLayoutService bentoLayoutService) {
        this.bentoLayoutService = bentoLayoutService;
    }

    @GetMapping("/{listId}")
    @Secured({ "ROLE_ADMIN", "ROLE_USER" })
    @Operation(summary = "Resolve the layout for a list surface (user → global → default)")
    public TurBentoLayoutResponse resolve(@PathVariable String listId) {
        return bentoLayoutService.resolve(listId);
    }

    @PutMapping("/{listId}")
    @Secured({ "ROLE_ADMIN", "ROLE_USER" })
    @Operation(summary = "Save the current user's protective layout override")
    public TurBentoLayoutResponse saveUser(@PathVariable String listId,
            @RequestBody List<TurBentoLayoutEntry> entries) {
        return bentoLayoutService.saveUser(listId, entries);
    }

    @PutMapping("/{listId}/global")
    @Secured({ "ROLE_ADMIN" })
    @Operation(summary = "Save the admin global template that non-customizers inherit")
    public TurBentoLayoutResponse saveGlobal(@PathVariable String listId,
            @RequestBody List<TurBentoLayoutEntry> entries) {
        return bentoLayoutService.saveGlobal(listId, entries);
    }

    @DeleteMapping("/{listId}")
    @Secured({ "ROLE_ADMIN", "ROLE_USER" })
    @Operation(summary = "Reset the current user's override to re-inherit the template/default")
    public TurBentoLayoutResponse resetUser(@PathVariable String listId) {
        return bentoLayoutService.resetUser(listId);
    }
}
