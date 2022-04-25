/*
 * Copyright (C) 2016-2019 Alexandre Oliveira <alexandre.oliveira@viglet.com> 
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
package com.viglet.turing.wem.index;



import com.viglet.turing.wem.config.IHandlerConfiguration;
import com.vignette.as.client.common.WhereClause;
import com.vignette.as.client.javabean.ContentInstance;
import com.vignette.logging.context.ContextLogger;

public interface IValidToIndex {
	
	static final ContextLogger log = ContextLogger.getLogger(IValidToIndex.class.getName());
	
	boolean isValid(ContentInstance ci, IHandlerConfiguration config) throws Exception;
	
	void whereToValid(WhereClause clause, IHandlerConfiguration config) throws Exception;

}
