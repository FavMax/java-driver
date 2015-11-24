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

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ConsistencyLevel;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.exceptions.ConnectionException;
import com.datastax.driver.core.exceptions.DriverException;
import com.datastax.driver.core.exceptions.OperationTimedOutException;

/**
 * A policy that defines a default behavior to adopt in the event of a timeout
 * or other unexpected exceptions.
 * <p>
 * This interface exists only for backward compatibility reasons: its methods should really be
 * defined by {@link RetryPolicy}, but adding it after the fact would break binary compatibility.
 * It should be merged into {@link RetryPolicy} in the next major release.
 * <p>
 * All retry policies shipped with the driver implement this interface.
 */
public interface ExtendedRetryPolicy extends RetryPolicy {

    /**
     * Defines whether to retry and at which consistency level on a
     * client timeout.
     *
     * @param statement the original query for which the consistency level cannot
     * be achieved.
     * @param cl the original consistency level for the operation.
     * @param nbRetry the number of retry already performed for this operation.
     * @return the retry decision. If {@code RetryDecision.RETHROW} is returned,
     * an {@link OperationTimedOutException} will be thrown for the operation.
     */
    RetryDecision onClientTimeout(Statement statement, ConsistencyLevel cl, int nbRetry);

    /**
     * Defines whether to retry and at which consistency level when the connection
     * encounters an error.
     *
     * @param statement the original query for which the consistency level cannot
     * be achieved.
     * @param cl the original consistency level for the operation.
     * @param e the original exception.
     * @param nbRetry the number of retry already performed for this operation.
     * @return the retry decision. If {@code RetryDecision.RETHROW} is returned,
     * the exception passed to this method will be rethrown for the operation.
     */
    RetryDecision onConnectionError(Statement statement, ConsistencyLevel cl, ConnectionException e, int nbRetry);

    /**
     * Defines whether to retry and at which consistency level when the contacted host
     * replies with an unexpected response, such as
     * {@link com.datastax.driver.core.exceptions.ServerError},
     * {@link com.datastax.driver.core.exceptions.OverloadedException} or
     * {@link com.datastax.driver.core.exceptions.BootstrappingException}.
     *
     * @param statement the original query for which the consistency level cannot
     * be achieved.
     * @param cl the original consistency level for the operation.
     * @param e the original exception.
     * @param nbRetry the number of retry already performed for this operation.
     * @return the retry decision. If {@code RetryDecision.RETHROW} is returned,
     * the exception passed to this method will be rethrown for the operation.
     */
    RetryDecision onUnexpectedError(Statement statement, ConsistencyLevel cl, DriverException e, int nbRetry);

}
