// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class DayBoundaryTest {
    private fun delay(instant: String, zone: String) = millisUntilNextDay(
        Clock.fixed(Instant.parse(instant), ZoneId.of("UTC")), ZoneId.of(zone))

    @Test fun shanghaiMonthEndHasOneMillisecondRemaining() {
        assertEquals(1L, delay("2026-10-31T15:59:59.999Z", "Asia/Shanghai"))
    }

    @Test fun springDstDayIsTwentyThreeHours() {
        assertEquals(23 * 3_600_000L, delay("2026-03-08T05:00:00Z", "America/New_York"))
    }

    @Test fun autumnDstDayIsTwentyFiveHours() {
        assertEquals(25 * 3_600_000L, delay("2026-11-01T04:00:00Z", "America/New_York"))
    }
}
