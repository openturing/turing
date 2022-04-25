/*
 * Copyright (C) 2016-2021 the original author or authors. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.viglet.turing.client.sn.pagination;

import java.util.ArrayList;
import java.util.List;

import com.viglet.turing.api.sn.bean.TurSNSiteSearchPaginationBean;

/**
 * Class to interact with current pagination.
 * 
 * @since 0.3.4
 */
public class TurSNPagination {

	List<TurSNSiteSearchPaginationBean> paginationList;

	private static String NEXT = "next";
	private static String PREVIOUS = "previous";
	private static String LAST = "last";
	private static String FIRST = "first";
	private static String CURRENT = "current";

	public TurSNPagination(List<TurSNSiteSearchPaginationBean> paginationList) {
		super();
		this.paginationList = paginationList;
	}

	public List<TurSNPaginationItem> getAllPages() {
		List<TurSNPaginationItem> allPages = new ArrayList<TurSNPaginationItem>();
		if (paginationList != null) {
			for (TurSNSiteSearchPaginationBean turSNPaginationItem : paginationList) {
				allPages.add(new TurSNPaginationItem(turSNPaginationItem));
			}
		}
		return allPages;
	}

	public TurSNPaginationItem findByType(String type) {
		if (paginationList != null) {
			for (TurSNSiteSearchPaginationBean paginationItem : paginationList) {
				if (paginationItem != null && paginationItem.getType() != null
						&& paginationItem.getType().toString().equals(type)) {
					return new TurSNPaginationItem(paginationItem);
				}
			}
		}
		return null;
	}

	public TurSNPaginationItem getCurrentPage() {
		return findByType(CURRENT);
	}

	public TurSNPaginationItem getNextPage() {
		return findByType(NEXT);
	}

	public TurSNPaginationItem getPreviousPage() {
		return findByType(PREVIOUS);
	}

	public TurSNPaginationItem getLastPage() {
		return findByType(LAST) != null ? findByType(LAST) : findByType(CURRENT);

	}

	public TurSNPaginationItem getFirstPage() {
		return findByType(FIRST) != null ? findByType(FIRST) : findByType(CURRENT);

	}

	public TurSNPaginationItem findByPageNumber(int pageNumber) {
		for (TurSNSiteSearchPaginationBean paginationItem : paginationList) {
			if (paginationItem.getPage() == pageNumber) {
				return new TurSNPaginationItem(paginationItem);
			}
		}
		return null;
	}

	public List<Integer> getPageNumberList() {
		List<Integer> numberList = new ArrayList<Integer>();
		for (TurSNSiteSearchPaginationBean paginationItem : paginationList) {
			numberList.add(paginationItem.getPage());
		}
		return numberList;
	}
}
