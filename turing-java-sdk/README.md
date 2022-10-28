
[![](https://jitpack.io/v/openturing/turing-java-sdk.svg)](https://jitpack.io/#openturing/turing-java-sdk)

# Java library to access Viglet Turing

Documentation: [https://openturing.github.io/turing-java-sdk/](https://openturing.github.io/turing-java-sdk/)

## Test Sample Code
```bash
./gradlew turing-java-sdk:shadowJar
java -cp turing-java-sdk/build/libs/turing-java-sdk-all.jar com.viglet.turing.client.sn.sample.TurSNClientSample
```

```java
package com.viglet.turing.client.sn.sample;

import com.viglet.turing.client.sn.HttpTurSNServer;
import com.viglet.turing.client.sn.TurSNDocumentList;
import com.viglet.turing.client.sn.TurSNQuery;
import com.viglet.turing.client.sn.response.QueryTurSNResponse;

public class TurSNClientSample {
	public static void main(String[] args) {

		HttpTurSNServer turSNServer = new HttpTurSNServer("http://localhost:2700/api/sn/Sample");

		TurSNQuery query = new TurSNQuery();
		query.setQuery("hello");
		query.setRows(10);
		query.setSortField(TurSNQuery.ORDER.asc);
		query.setPageNumber(1);

		QueryTurSNResponse response = turSNServer.query(query);
		TurSNDocumentList turSNResults = response.getResults();
		TurSNPagination turSNPagination = response.getPagination();
		
		turSNPagination.getAllPages().forEach(page -> {
			System.out.println(page.getLabel());
		});
		
		System.out.println("---");
		
		turSNPagination.getLastPage().ifPresent(page -> System.out.println(page.getLabel()));
}
