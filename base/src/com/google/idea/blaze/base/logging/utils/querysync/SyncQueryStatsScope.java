/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.logging.utils.querysync;

import com.google.common.annotations.VisibleForTesting;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.BlazeScope;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/** Stores @{SyncQueryStatsScope} so that it can be logged by the BlazeContext creator owner. */
public class SyncQueryStatsScope implements BlazeScope {
  private Instant startTime;
  private final SyncQueryStats.Builder builder;

  public SyncQueryStatsScope() {
    builder = SyncQueryStats.builder();
  }

  @VisibleForTesting
  public SyncQueryStats.Builder getBuilder() {
    return builder;
  }

  public static Optional<SyncQueryStats.Builder> fromContext(BlazeContext context) {
    return Optional.ofNullable(context.getScope(SyncQueryStatsScope.class))
        .map(SyncQueryStatsScope::getBuilder);
  }

  @Override
  public void onScopeBegin(BlazeContext context) {
    startTime = Instant.now();
  }

  /** Called when the context scope is ending. */
  @Override
  public void onScopeEnd(BlazeContext context) {
    QuerySyncActionStatsScope.fromContext(context)
        .ifPresent(
            stats ->
                stats.addOperationStats(
                    builder.setTotalClockTime(Duration.between(startTime, Instant.now())).build()));
  }
}
