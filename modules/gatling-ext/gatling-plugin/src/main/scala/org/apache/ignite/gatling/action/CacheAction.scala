/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ignite.gatling.action

import com.typesafe.scalalogging.StrictLogging
import io.gatling.commons.validation.Failure
import io.gatling.commons.validation.SuccessWrapper
import io.gatling.commons.validation.Validation
import io.gatling.core.action.Action
import io.gatling.core.session.Session
import io.gatling.core.structure.ScenarioContext
import org.apache.ignite.gatling.api.CacheApi
import org.apache.ignite.gatling.api.TransactionApi
import org.apache.ignite.gatling.protocol.IgniteProtocol.ExplicitLockWasUsedSessionKey

/**
 * Base action class for cache operations.
 *
 * @tparam K Type of the cache key.
 * @tparam V Type of the cache value.
 * @param actionType Action type name.
 * @param requestName Name of the request provided via the DSL. May be empty. If so name will be generated
 *                    automatically based on action type and cache name.
 * @param ctx Gatling scenario context.
 * @param next Next action to execute in scenario chain.
 * @param cacheName Name of cache.
 * @param keepBinary True if it should operate with binary objects.
 * @param async True if async API should be used.
 */
abstract class CacheAction[K, V](
    actionType: String,
    requestName: String,
    ctx: ScenarioContext,
    next: Action,
    val cacheName: String,
    val keepBinary: Boolean,
    val async: Boolean = false
) extends IgniteAction(actionType, requestName, ctx, next)
    with StrictLogging {

    override val request: String = if (requestName == "") s"$name $cacheName" else requestName

    /**
     * Common parameters for cache actions.
     *
     * @param cacheApi Instance of CacheApi.
     * @param transactionApi Instance of TransactionApi.
     */
    case class CacheActionParameters(cacheApi: CacheApi[K, V], transactionApi: Option[TransactionApi])

    /**
     * Resolves cache action parameters using session context.
     *
     * @param session Session.
     * @return Instance of CacheActionParameters
     */
    def resolveCacheParameters(session: Session): Validation[CacheActionParameters] =
        for {
            IgniteActionParameters(igniteApi, transactionApi) <- resolveIgniteParameters(session)

            explicitLocksWereUsed <- session(ExplicitLockWasUsedSessionKey).asOption[Boolean].success

            cacheApi <- {
                if (async && (explicitLocksWereUsed.nonEmpty || transactionApi.isDefined)) {
                    Failure("Async Ignite API can not be used in transaction context or along with the explicit locks.")
                } else {
                    if (keepBinary) {
                        igniteApi.cache[K, V](cacheName).map(cache => cache.withKeepBinary())
                    } else {
                        igniteApi.cache[K, V](cacheName)
                    }
                }
            }
        } yield CacheActionParameters(cacheApi, transactionApi)
}
