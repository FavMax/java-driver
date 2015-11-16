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

import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.DriverException;

/**
 * A policy that defines a default behavior to adopt when a request returns
 * a TimeoutException or an UnavailableException.
 *
 * Such policy allows to centralize the handling of query retries, allowing to
 * minimize the need for exception catching/handling in business code.
 */
public interface ClientFailureAwareRetryPolicy extends RetryPolicy {

    /**
     * Defines whether to retry and at which consistency level on a
     * client timeout.
     *
     * @param statement the original query for which the consistency level cannot
     * be achieved.
     * @param cl the original consistency level for the operation.
     * @param nbRetry the number of retry already performed for this operation.
     * @return the retry decision. If {@code RetryDecision.RETHROW} is returned,
     * an {@link com.datastax.driver.core.OperationTimedOutException} will
     * be thrown for the operation.
     */
    RetryDecision onClientTimeout(Statement statement, ConsistencyLevel cl, int nbRetry);

    /**
     * Defines whether to retry and at which consistency level on an
     * unexpected exception.
     *
     * @param statement the original query for which the consistency level cannot
     * be achieved.
     * @param cl the original consistency level for the operation.
     * @param e the original exception.
     * @param nbRetry the number of retry already performed for this operation.
     * @return the retry decision. If {@code RetryDecision.RETHROW} is returned,
     * the exception passed to this method will
     * be rethrown for the operation.
     */
    RetryDecision onUnexpectedException(Statement statement, ConsistencyLevel cl, DriverException e, int nbRetry);

}
