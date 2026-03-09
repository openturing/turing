package com.viglet.turing.client.sn;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Date;
import java.util.Locale;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.junit.jupiter.api.Test;

import com.viglet.turing.client.sn.response.QueryTurSNResponse;

class TurSNServerInternalTest {

    @Test
    void shouldPrepareGetQueryRequestWithExpectedParameters() throws Exception {
        TurSNServer turSNServer = new TurSNServer(URI.create("http://localhost:2700"), "Portal", Locale.US);
        TurSNQuery turSNQuery = new TurSNQuery();
        turSNQuery.setQuery("hello");
        turSNQuery.setRows(10);
        turSNQuery.setGroupBy("type");
        turSNQuery.addFilterQuery("type:Page");
        turSNQuery.setSortField(TurSNQuery.Order.desc);
        turSNQuery.setBetweenDates("created", new Date(0L), new Date(1_000L));
        turSNQuery.setPageNumber(0);
        turSNQuery.setPopulateMetrics(true);

        turSNServer.setTurSNQuery(turSNQuery);

        HttpUriRequestBase request = invokePrepareQueryRequest(turSNServer);

        assertThat(request).isInstanceOf(HttpGet.class);
        String uri = request.getUri().toString();
        assertThat(uri)
                .contains("/api/sn/Portal/search")
                .contains("locale=en-US")
                .contains("q=hello")
                .contains("group=type")
                .contains("rows=10")
                .contains("fq%5B%5D=type%3APage")
                .contains("sort=newest")
                .contains("p=1")
                .contains("created%3A%5B");
    }

    @Test
    void shouldReturnEmptyResponseWhenConnectionFails() {
        TurSNServer turSNServer = new TurSNServer(URI.create("http://localhost:1"), "Portal", Locale.US);
        TurSNQuery turSNQuery = new TurSNQuery();
        turSNQuery.setQuery("hello");

        QueryTurSNResponse response = turSNServer.query(turSNQuery);

        assertThat(response).isNotNull();
        assertThat(response.getResults()).isNull();
        assertThat(response.getGroupResponse()).isNull();
    }

    private static HttpUriRequestBase invokePrepareQueryRequest(TurSNServer turSNServer) throws Exception {
        Method method = TurSNServer.class.getDeclaredMethod("prepareQueryRequest");
        method.setAccessible(true);
        return (HttpUriRequestBase) method.invoke(turSNServer);
    }
}
