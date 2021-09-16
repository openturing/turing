/*
 * Copyright (C) 2016-2021 the original author or authors. 
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

package com.viglet.turing.api;

import java.io.UnsupportedEncodingException;

import org.json.JSONException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import org.tensorflow.Graph;
import org.tensorflow.Session;
import org.tensorflow.Tensor;
import org.tensorflow.TensorFlow;

@RestController
@RequestMapping("/api/temp")
@Api(value="/temp", tags="Heartbeat", description="Heartbeat")
public class TurAPITemp {

	@GetMapping
	public void info() throws JSONException, UnsupportedEncodingException {

		try (Graph g = new Graph()) {
		      final String value = "Hello from " + TensorFlow.version();

		      // Construct the computation graph with a single operation, a constant
		      // named "MyConst" with a value "value".
		      try (Tensor<?> t = Tensor.create(value.getBytes("UTF-8"))) {
		        // The Java API doesn't yet include convenience functions for adding operations.
		        g.opBuilder("Const", "MyConst").setAttr("dtype", t.dataType()).setAttr("value", t).build();
		      }

		      // Execute the "MyConst" operation in a Session.
		      try (Session s = new Session(g);
		          // Generally, there may be multiple output tensors,
		          // all of them must be closed to prevent resource leaks.
		          Tensor<?> output = s.runner().fetch("MyConst").run().get(0)) {
		        System.out.println(new String(output.bytesValue(), "UTF-8"));
		      }
		    }

	}
}