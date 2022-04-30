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

package com.viglet.turing.api.sn.console;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */

@RestController
@RequestMapping("/api/sn/{snSiteId}/metrics")
@Tag(name = "Semantic Navigation Metrics", description = "Semantic Navigation Metrics API")
public class TurSNSiteMetricsAPI {

	@Autowired
	private TurSNSiteRepository turSNSiteRepository;
	@Autowired
	private TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository;

	@Operation(summary = "Semantic Navigation Site Metrics Top Terms")
	@GetMapping("top-terms/today/{rows}")
	public List<TurSNSiteMetricAccessTerm> turSNSiteMetricsTopTermsToday(@PathVariable String snSiteId,
			@PathVariable int rows) {
		Optional<TurSNSite> turSNSite = turSNSiteRepository.findById(snSiteId);
		if (turSNSite.isPresent()) {
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.clear(Calendar.MINUTE);
			cal.clear(Calendar.SECOND);
			cal.clear(Calendar.MILLISECOND);
			return this.turSNSiteMetricAccessRepository.topTermsBetweenDates(turSNSite.get(), cal.getTime(), new Date(),
					PageRequest.of(0, rows));
		}
		return new ArrayList<>();
	}

	@GetMapping("top-terms/this-week/{rows}")
	public List<TurSNSiteMetricAccessTerm> turSNSiteMetricsTopTermsThisWeek(@PathVariable String snSiteId,
			@PathVariable int rows) {
		Optional<TurSNSite> turSNSite = turSNSiteRepository.findById(snSiteId);
		if (turSNSite.isPresent()) {
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.clear(Calendar.MINUTE);
			cal.clear(Calendar.SECOND);
			cal.clear(Calendar.MILLISECOND);

			cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());

			return this.turSNSiteMetricAccessRepository.topTermsBetweenDates(turSNSite.get(), cal.getTime(), new Date(),
					PageRequest.of(0, rows));
		}
		return new ArrayList<>();
	}

	@GetMapping("top-terms/this-month/{rows}")
	public List<TurSNSiteMetricAccessTerm> turSNSiteMetricsTopTermsThisMonth(@PathVariable String snSiteId,
			@PathVariable int rows) {
		Optional<TurSNSite> turSNSite = turSNSiteRepository.findById(snSiteId);
		if (turSNSite.isPresent()) {
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.clear(Calendar.MINUTE);
			cal.clear(Calendar.SECOND);
			cal.clear(Calendar.MILLISECOND);

			cal.set(Calendar.DAY_OF_MONTH, 1);

			return this.turSNSiteMetricAccessRepository.topTermsBetweenDates(turSNSite.get(), cal.getTime(), new Date(),
					PageRequest.of(0, rows));
		}
		return new ArrayList<>();
	}

	@GetMapping("top-terms/all-time/{rows}")
	public List<TurSNSiteMetricAccessTerm> turSNSiteMetricsTopTermsAllTime(@PathVariable String snSiteId,
			@PathVariable int rows) {
		Optional<TurSNSite> turSNSite = turSNSiteRepository.findById(snSiteId);
		if (turSNSite.isPresent()) {
			return this.turSNSiteMetricAccessRepository.topTerms(turSNSite.get(), PageRequest.of(0, rows));
		}
		return new ArrayList<>();
	}
}