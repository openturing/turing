/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.mcp.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Unit tests for {@link TurMcpLoopbackFilter} — the T245 loopback-first trust
 * boundary on {@code /mcp}.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurMcpLoopbackFilterTest {

    private MockHttpServletRequest request(String remoteAddr) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/mcp");
        req.setRemoteAddr(remoteAddr);
        return req;
    }

    @Test
    void loopbackOnly_rejectsRemoteAddress() throws Exception {
        TurMcpLoopbackFilter filter = new TurMcpLoopbackFilter(true);
        MockHttpServletRequest req = request("203.0.113.7");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, res.getStatus());
        // The request must not have been forwarded down the chain.
        assertNull(chain.getRequest(), "non-loopback request should not reach the chain");
    }

    @ParameterizedTest(name = "{0} loopback should reach the chain")
    @CsvSource({
            "127.0.0.1,         loopback request should reach the chain",
            "0:0:0:0:0:0:0:1,   IPv6 loopback request should reach the chain"
    })
    void loopbackOnly_allowsLoopback(String remoteAddr, String message) throws Exception {
        TurMcpLoopbackFilter filter = new TurMcpLoopbackFilter(true);
        MockHttpServletRequest req = request(remoteAddr);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(HttpServletResponse.SC_OK, res.getStatus());
        assertNotNull(chain.getRequest(), message);
    }

    @Test
    void loopbackDisabled_allowsRemoteAddress() throws Exception {
        TurMcpLoopbackFilter filter = new TurMcpLoopbackFilter(false);
        MockHttpServletRequest req = request("203.0.113.7");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertEquals(HttpServletResponse.SC_OK, res.getStatus());
        assertNotNull(chain.getRequest(), "remote request should pass when loopback-only is off");
    }
}
