/*
 * Copyright (C) 2016-2022 the original author or authors.
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
package com.viglet.turing.connector.db;

/**
 * Class that can be used create Chunking Job for Turing AI Import
 *
 * @author Alexandre Oliveira
 * @since 0.3.5
 **/

import com.viglet.turing.client.sn.job.TurSNJobItem;
import com.viglet.turing.client.sn.job.TurSNJobItems;

public class TurDbChunkingJob {
    private int chunkSize;
    private int chunkCurrent;
    private int total;
    private TurSNJobItems turSNJobItems;

    public TurDbChunkingJob(int chunkSize) {
        super();
        this.chunkSize = chunkSize;
        this.chunkCurrent = 0;
        this.total = 0;
        this.turSNJobItems = new TurSNJobItems();
    }

    public boolean hasItemsLeft() {
        return this.chunkCurrent > 0;
    }

    public int getFirstItemPosition() {
        int left = total % chunkSize;
        if (left > 0) {
            return total - left + 1;
        } else {
            return (this.total > this.chunkSize) ? this.total - this.chunkSize + 1 : 1;
        }
    }

    public void addItem(TurSNJobItem turSNJobItem) {
        this.turSNJobItems.add(turSNJobItem);
        this.total++;
        this.chunkCurrent++;
    }

    public void newCicle() {
        this.chunkCurrent = 0;
        this.turSNJobItems = new TurSNJobItems();
    }

    public boolean isChunkLimit() {
        return this.chunkCurrent == this.chunkSize;
    }

    public int getCurrent() {
        return this.chunkCurrent;
    }

    public int getTotal() {
        return this.total;
    }

    public TurSNJobItems getTurSNJobItems() {
        return this.turSNJobItems;
    }

    public int getChunkSize() {
        return this.chunkSize;
    }
}
