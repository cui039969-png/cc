// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.domain

import java.time.Clock
import java.time.Duration
import java.time.ZoneId

/** A local day may last 23 or 25 hours; never assume a fixed 24-hour duration. */
fun millisUntilNextDay(clock: Clock, zone: ZoneId): Long {
    val now = clock.instant()
    val next = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
    return Duration.between(now, next).toMillis().coerceAtLeast(1)
}
