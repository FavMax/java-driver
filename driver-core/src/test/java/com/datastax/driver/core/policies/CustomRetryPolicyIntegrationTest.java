/*
 *      Copyright (C) 2012-2015 DataStax Inc.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.datastax.driver.core.policies;

import org.scassandra.http.client.PrimingRequest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.scassandra.http.client.PrimingRequest.Result.is_bootstrapping;
import static org.scassandra.http.client.PrimingRequest.Result.overloaded;
import static org.scassandra.http.client.PrimingRequest.Result.read_request_timeout;
import static org.scassandra.http.client.PrimingRequest.Result.server_error;
import static org.scassandra.http.client.PrimingRequest.Result.unavailable;

import com.datastax.driver.core.*;
import com.datastax.driver.core.exceptions.*;

/**
 * Integration test with a custom implementation, to test retry and ignore decisions.
 */
public class CustomRetryPolicyIntegrationTest extends AbstractRetryPolicyIntegrationTest {
    public CustomRetryPolicyIntegrationTest() {
        super(new CustomRetryPolicy());
    }

    @Test(groups = "short")
    public void should_ignore_read_timeout() {
        simulateError(1, read_request_timeout);

        ResultSet rs = query();
        assertThat(rs.iterator().hasNext()).isFalse(); // ignore decisions produce empty result sets

        assertOnReadTimeoutWasCalled(1);
        assertThat(errors.getIgnores().getCount()).isEqualTo(1);
        assertThat(errors.getRetries().getCount()).isEqualTo(0);
        assertThat(errors.getIgnoresOnReadTimeout().getCount()).isEqualTo(1);
        assertThat(errors.getRetriesOnReadTimeout().getCount()).isEqualTo(0);
        assertQueried(1, 1);
        assertQueried(2, 0);
        assertQueried(3, 0);
    }

    @Test(groups = "short")
    public void should_retry_once_on_same_host_on_unavailable() {
        simulateError(1, unavailable);

        try {
            query();
            fail("expected an UnavailableException");
        } catch (UnavailableException e) {/*expected*/}

        assertOnUnavailableWasCalled(2);
        assertThat(errors.getRetries().getCount()).isEqualTo(1);
        assertThat(errors.getUnavailables().getCount()).isEqualTo(2);
        assertThat(errors.getRetriesOnUnavailable().getCount()).isEqualTo(1);
        assertQueried(1, 2);
        assertQueried(2, 0);
        assertQueried(3, 0);
    }

    @Test(groups = "short")
    public void should_try_next_host_on_client_timeouts() {
        cluster.getConfiguration().getSocketOptions().setReadTimeoutMillis(1);
        scassandras
            .prime(1, PrimingRequest.queryBuilder()
                .withQuery("mock query")
                .withFixedDelay(1000)
                .withRows(row("result", "result1"))
                .build())
            .prime(2, PrimingRequest.queryBuilder()
                .withQuery("mock query")
                .withFixedDelay(1000)
                .withRows(row("result", "result2"))
                .build())
            .prime(3, PrimingRequest.queryBuilder()
                .withQuery("mock query")
                .withFixedDelay(1000)
                .withRows(row("result", "result3"))
                .build());
        try {
            query();
            fail("expected a NoHostAvailableException");
        } catch (NoHostAvailableException e) {
            assertThat(e.getErrors().keySet()).hasSize(3).containsExactly(
                host1.getSocketAddress(),
                host2.getSocketAddress(),
                host3.getSocketAddress());
            assertThat(e.getErrors().values()).hasOnlyElementsOfType(OperationTimedOutException.class);
        }
        assertOnClientTimeoutWasCalled(3);
        assertThat(errors.getRetries().getCount()).isEqualTo(3);
        assertThat(errors.getClientTimeouts().getCount()).isEqualTo(3);
        assertThat(errors.getRetriesOnClientTimeout().getCount()).isEqualTo(3);
        assertQueried(1, 1);
        assertQueried(2, 1);
        assertQueried(3, 1);
    }

    @DataProvider
    public static Object[][] unexpectedErrors() {
        return new Object[][]{
            {server_error, ServerError.class},
            {overloaded, BootstrappingException.class},
            {is_bootstrapping, BootstrappingException.class}
        };
    }

    @Test(groups = "short", dataProvider = "unexpectedErrors")
    public void should_rethrow_unexpected_exception(PrimingRequest.Result error, Class<? extends DriverException> exception) {
        simulateError(1, error);

        try {
            query();
            fail("expected " + error);
        } catch (DriverInternalError e) {/*expected*/}

        assertOnUnexpectedExceptionWasCalled(1, exception);
        assertThat(errors.getOthers().getCount()).isEqualTo(1);
        assertQueried(1, 1);
        assertQueried(2, 0);
        assertQueried(3, 0);
    }

    /**
     * Ignores read and write timeouts, and retries at most once on unavailable.
     */
    static class CustomRetryPolicy implements ExtendedRetryPolicy {

        @Override
        public RetryDecision onReadTimeout(Statement statement, ConsistencyLevel cl, int requiredResponses, int receivedResponses, boolean dataRetrieved, int nbRetry) {
            return RetryDecision.ignore();
        }

        @Override
        public RetryDecision onWriteTimeout(Statement statement, ConsistencyLevel cl, WriteType writeType, int requiredAcks, int receivedAcks, int nbRetry) {
            return RetryDecision.ignore();
        }

        @Override
        public RetryDecision onUnavailable(Statement statement, ConsistencyLevel cl, int requiredReplica, int aliveReplica, int nbRetry) {
            return (nbRetry == 0)
                ? RetryDecision.retry(cl)
                : RetryDecision.rethrow();
        }

        @Override
        public RetryDecision onClientTimeout(Statement statement, ConsistencyLevel cl, int nbRetry) {
            return RetryDecision.tryNextHost(cl);
        }

        @Override
        public RetryDecision onConnectionError(Statement statement, ConsistencyLevel cl, ConnectionException e, int nbRetry) {
            return RetryDecision.tryNextHost(cl);
        }

        @Override
        public RetryDecision onUnexpectedError(Statement statement, ConsistencyLevel cl, DriverException e, int nbRetry) {
            return RetryDecision.rethrow();
        }
    }
}
