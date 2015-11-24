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

import org.assertj.core.api.Fail;
import org.scassandra.http.client.PrimingRequest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.scassandra.http.client.PrimingRequest.Result.is_bootstrapping;
import static org.scassandra.http.client.PrimingRequest.Result.overloaded;
import static org.scassandra.http.client.PrimingRequest.Result.server_error;
import static org.scassandra.http.client.PrimingRequest.Result.write_request_timeout;

import com.datastax.driver.core.*;
import com.datastax.driver.core.exceptions.*;

/**
 * Integration test with a IdempotenceAwareRetryPolicy.
 */
public class IdempotenceAwareRetryPolicyIntegrationTest extends AbstractRetryPolicyIntegrationTest {
    public IdempotenceAwareRetryPolicyIntegrationTest() {
        super(new IdempotenceAwareRetryPolicy(new CustomRetryPolicy(), new QueryOptions().setDefaultIdempotence(false)));
    }

    @Test(groups = "short")
    public void should_not_retry_on_write_timeout_if_statement_non_idempotent() {
        simulateError(1, write_request_timeout);

        try {
            query();
            fail("expected an WriteTimeoutException");
        } catch (WriteTimeoutException e) {/* expected */}

        assertOnWriteTimeoutWasCalled(1);
        assertThat(errors.getWriteTimeouts().getCount()).isEqualTo(1);
        assertThat(errors.getRetries().getCount()).isEqualTo(0);
        assertThat(errors.getRetriesOnWriteTimeout().getCount()).isEqualTo(0);
        assertQueried(1, 1);
        assertQueried(2, 0);
        assertQueried(3, 0);
    }

    @Test(groups = "short")
    public void should_not_retry_on_client_timeout_if_statement_non_idempotent() {
        cluster.getConfiguration().getSocketOptions().setReadTimeoutMillis(1);
        try {
            scassandras
                .prime(1, PrimingRequest.queryBuilder()
                    .withQuery("mock query")
                    .withFixedDelay(1000)
                    .withRows(row("result", "result1"))
                    .build());
            try {
                query();
                Fail.fail("expected a NoHostAvailableException");
            } catch (OperationTimedOutException e) {/* expected */}

            assertOnClientTimeoutWasCalled(1);
            assertThat(errors.getClientTimeouts().getCount()).isEqualTo(1);
            assertThat(errors.getRetries().getCount()).isEqualTo(0);
            assertThat(errors.getRetriesOnClientTimeout().getCount()).isEqualTo(0);
            assertQueried(1, 1);
            assertQueried(2, 0);
            assertQueried(3, 0);
        } finally {
            cluster.getConfiguration().getSocketOptions().setReadTimeoutMillis(SocketOptions.DEFAULT_READ_TIMEOUT_MILLIS);
        }
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
    public void should_not_retry_on_unexpected_exception_if_statement_non_idempotent(PrimingRequest.Result error, Class<? extends DriverException> exception) {
        simulateError(1, error);

        try {
            query();
            Fail.fail("expected " + error);
        } catch (DriverInternalError e) {/*expected*/}

        assertOnUnexpectedErrorWasCalled(1, exception);
        assertThat(errors.getOthers().getCount()).isEqualTo(1);
        assertThat(errors.getRetries().getCount()).isEqualTo(0);
        assertThat(errors.getRetriesOnUnexpectedError().getCount()).isEqualTo(0);
        assertQueried(1, 1);
        assertQueried(2, 0);
        assertQueried(3, 0);
    }


    /**
     * Retries everything, but since all statements are non idempotent, nothing should actually be retried.
     */
    static class CustomRetryPolicy implements ExtendedRetryPolicy {

        @Override
        public RetryDecision onReadTimeout(Statement statement, ConsistencyLevel cl, int requiredResponses, int receivedResponses, boolean dataRetrieved, int nbRetry) {
            return RetryDecision.retry(cl);
        }

        @Override
        public RetryDecision onWriteTimeout(Statement statement, ConsistencyLevel cl, WriteType writeType, int requiredAcks, int receivedAcks, int nbRetry) {
            return RetryDecision.retry(cl);
        }

        @Override
        public RetryDecision onUnavailable(Statement statement, ConsistencyLevel cl, int requiredReplica, int aliveReplica, int nbRetry) {
            return RetryDecision.retry(cl);
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
            return RetryDecision.tryNextHost(cl);
        }
    }
}
