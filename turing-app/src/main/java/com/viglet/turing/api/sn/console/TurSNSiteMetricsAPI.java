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
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.viglet.turing.api.sn.bean.TurSNSiteMetricsTopTermsBean;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;

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
	public TurSNSiteMetricsTopTermsBean turSNSiteMetricsTopTermsToday(@PathVariable String snSiteId,
			@PathVariable int rows) {
		Optional<TurSNSite> turSNSite = turSNSiteRepository.findById(snSiteId);
		if (turSNSite.isPresent()) {
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.clear(Calendar.MINUTE);
			cal.clear(Calendar.SECOND);
			cal.clear(Calendar.MILLISECOND);

			Calendar previousBegin = Calendar.getInstance();
			previousBegin.set(Calendar.HOUR_OF_DAY, 0);
			previousBegin.clear(Calendar.MINUTE);
			previousBegin.clear(Calendar.SECOND);
			previousBegin.clear(Calendar.MILLISECOND);
			previousBegin.add(Calendar.DATE, -1);
			
			Calendar previousEnd = Calendar.getInstance();
			previousEnd.add(Calendar.DATE, -1);
			
			return getTopTermsReport(rows, turSNSite, cal, previousBegin, previousEnd);
		}
		return new TurSNSiteMetricsTopTermsBean(new ArrayList<>(), 0, 0);
	}

	private TurSNSiteMetricsTopTermsBean getTopTermsReport(int rows, Optional<TurSNSite> turSNSite, Calendar cal,
			Calendar previousBegin, Calendar previousEnd) {
		return new TurSNSiteMetricsTopTermsBean(
				this.turSNSiteMetricAccessRepository.topTermsBetweenDates(turSNSite.get(), cal.getTime(),
						new Date(), PageRequest.of(0, rows)),
				this.turSNSiteMetricAccessRepository.countTermsByPeriod(turSNSite.get(), cal.getTime(), new Date()),
				this.turSNSiteMetricAccessRepository.countTermsByPeriod(turSNSite.get(), previousBegin.getTime(),
						previousEnd.getTime()));
	}

	@GetMapping("top-terms/this-week/{rows}")
	public TurSNSiteMetricsTopTermsBean turSNSiteMetricsTopTermsThisWeek(@PathVariable String snSiteId,
			@PathVariable int rows) {
		Optional<TurSNSite> turSNSite = turSNSiteRepository.findById(snSiteId);
		if (turSNSite.isPresent()) {
			Calendar cal = Calendar.getInstance();
			
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.clear(Calendar.MINUTE);
			cal.clear(Calendar.SECOND);
			cal.clear(Calendar.MILLISECOND);
			cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
			
			Calendar previousBegin = Calendar.getInstance();
			previousBegin.add(Calendar.DATE, -7);
			previousBegin.set(Calendar.HOUR_OF_DAY, 0);
			previousBegin.clear(Calendar.MINUTE);
			previousBegin.clear(Calendar.SECOND);
			previousBegin.clear(Calendar.MILLISECOND);
			previousBegin.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
			
			Calendar previousEnd = Calendar.getInstance();
			previousEnd.add(Calendar.DATE, -7);

			return getTopTermsReport(rows, turSNSite, cal, previousBegin, previousEnd);
		}
		return new TurSNSiteMetricsTopTermsBean(new ArrayList<>(), 0, 0);
	}

	@GetMapping("top-terms/this-month/{rows}")
	public TurSNSiteMetricsTopTermsBean turSNSiteMetricsTopTermsThisMonth(@PathVariable String snSiteId,
			@PathVariable int rows) {
		Optional<TurSNSite> turSNSite = turSNSiteRepository.findById(snSiteId);
		if (turSNSite.isPresent()) {
			Calendar cal = Calendar.getInstance();
			cal.set(Calendar.HOUR_OF_DAY, 0);
			cal.clear(Calendar.MINUTE);
			cal.clear(Calendar.SECOND);
			cal.clear(Calendar.MILLISECOND);

			cal.set(Calendar.DAY_OF_MONTH, 1);

			
			Calendar previousBegin = Calendar.getInstance();
			previousBegin.set(Calendar.HOUR_OF_DAY, 0);
			previousBegin.clear(Calendar.MINUTE);
			previousBegin.clear(Calendar.SECOND);
			previousBegin.clear(Calendar.MILLISECOND);
			previousBegin.add(Calendar.MONTH, -1);
			previousBegin.set(Calendar.DATE, 1);
			
			Calendar previousEnd = Calendar.getInstance();
			previousEnd.add(Calendar.DAY_OF_MONTH, (-1) * previousEnd.getActualMaximum(Calendar.DAY_OF_MONTH) + 1);
			
			return getTopTermsReport(rows, turSNSite, cal, previousBegin, previousEnd);
		}
		return new TurSNSiteMetricsTopTermsBean(new ArrayList<>(), 0, 0);
	}

	@GetMapping("top-terms/all-time/{rows}")
	public TurSNSiteMetricsTopTermsBean turSNSiteMetricsTopTermsAllTime(@PathVariable String snSiteId,
			@PathVariable int rows) {
		Optional<TurSNSite> turSNSite = turSNSiteRepository.findById(snSiteId);
		if (turSNSite.isPresent()) {
			return new TurSNSiteMetricsTopTermsBean(
					this.turSNSiteMetricAccessRepository.topTerms(turSNSite.get(), PageRequest.of(0, rows)),
					this.turSNSiteMetricAccessRepository.countTerms(turSNSite.get()), 0);
		}
		return new TurSNSiteMetricsTopTermsBean(new ArrayList<>(), 0, 0);
	}

}