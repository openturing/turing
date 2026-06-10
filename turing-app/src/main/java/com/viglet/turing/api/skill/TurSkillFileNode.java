/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.api.skill;

/**
 * T318 / §IX.4.d — a single entry of a skill folder's file tree, addressed by a
 * path <strong>relative to the skill root</strong> (never the absolute storage
 * object name). This is what keeps the mini-VS-Code editor isolated to one
 * skill: the client only ever sees and sends paths inside the skill folder, so
 * there is no way to navigate above the skill's root.
 *
 * @param path     path relative to the skill root, e.g. {@code SKILL.md} or
 *                 {@code scripts/build.py} (forward-slash separated, no leading
 *                 slash)
 * @param name     the last path segment (display label)
 * @param directory whether this node is a folder
 * @param size     byte size for files; {@code 0} for folders
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurSkillFileNode(String path, String name, boolean directory, long size) {
}
