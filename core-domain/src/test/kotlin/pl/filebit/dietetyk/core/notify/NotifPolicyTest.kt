package pl.filebit.dietetyk.core.notify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotifPolicyTest {

    private val noon = 12 * 60

    @Test fun `master off blokuje wszystko`() {
        val d = NotifPolicy.decide(NotifKind.RED_FLAG, NotifLevel.COACH, noon, 0, masterOn = false)
        assertFalse(d.send); assertEquals("master_off", d.reason)
    }

    @Test fun `cisza nocna blokuje wszystko - takze red-flag`() {
        assertTrue(NotifPolicy.inQuietHours(22 * 60))
        assertTrue(NotifPolicy.inQuietHours(6 * 60))
        assertFalse(NotifPolicy.inQuietHours(7 * 60))       // 7:00 już OK
        assertFalse(NotifPolicy.inQuietHours(21 * 60 + 29)) // 21:29 jeszcze OK
        assertTrue(NotifPolicy.inQuietHours(21 * 60 + 30))  // 21:30 cisza
        val d = NotifPolicy.decide(NotifKind.RED_FLAG, NotifLevel.COACH, 23 * 60, 0, true)
        assertFalse(d.send); assertEquals("quiet_hours", d.reason)
    }

    @Test fun `MINIMAL przepuszcza tylko wizyte i red-flag`() {
        assertTrue(NotifPolicy.decide(NotifKind.WEEKLY_VISIT, NotifLevel.MINIMAL, noon, 0, true).send)
        assertTrue(NotifPolicy.decide(NotifKind.RED_FLAG, NotifLevel.MINIMAL, noon, 0, true).send)
        assertFalse(NotifPolicy.decide(NotifKind.MORNING, NotifLevel.MINIMAL, noon, 0, true).send)
        assertFalse(NotifPolicy.decide(NotifKind.MILESTONE, NotifLevel.MINIMAL, noon, 0, true).send)
        assertEquals("below_level", NotifPolicy.decide(NotifKind.MEAL_NUDGE, NotifLevel.MINIMAL, noon, 0, true).reason)
    }

    @Test fun `pre-meal tylko na COACH`() {
        assertFalse(NotifPolicy.decide(NotifKind.PRE_MEAL, NotifLevel.BALANCED, noon, 0, true).send)
        assertTrue(NotifPolicy.decide(NotifKind.PRE_MEAL, NotifLevel.COACH, noon, 0, true).send)
    }

    @Test fun `sufit dzienny blokuje codzienne ale NIE wazne`() {
        // 3 codzienne już poszły → kolejne codzienne blokowane
        val daily = NotifPolicy.decide(NotifKind.EVENING, NotifLevel.BALANCED, noon, NotifPolicy.DAILY_CAP, true)
        assertFalse(daily.send); assertEquals("daily_cap", daily.reason)
        // ważne (wizyta/red-flag/milestone) przechodzi mimo wyczerpanego sufitu
        assertTrue(NotifPolicy.decide(NotifKind.WEEKLY_VISIT, NotifLevel.BALANCED, noon, 99, true).send)
        assertTrue(NotifPolicy.decide(NotifKind.MILESTONE, NotifLevel.BALANCED, noon, 99, true).send)
    }

    @Test fun `kanaly - wazne vs codzienne`() {
        assertTrue(NotifPolicy.decide(NotifKind.RED_FLAG, NotifLevel.BALANCED, noon, 0, true).channelImportant)
        assertFalse(NotifPolicy.decide(NotifKind.MORNING, NotifLevel.BALANCED, noon, 0, true).channelImportant)
    }

    @Test fun `BALANCED przepuszcza codzienne pod sufitem`() {
        assertTrue(NotifPolicy.decide(NotifKind.MORNING, NotifLevel.BALANCED, 8 * 60, 0, true).send)
        assertTrue(NotifPolicy.decide(NotifKind.MEAL_NUDGE, NotifLevel.BALANCED, 14 * 60, 2, true).send) // 2<3
    }
}
