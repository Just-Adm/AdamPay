package com.adampay.app.domain

import com.adampay.app.domain.cash.CountDifference
import com.adampay.app.domain.cash.CountSheet
import com.adampay.app.domain.cash.Denominations
import com.adampay.app.domain.cash.Explanation
import com.adampay.app.domain.cash.Resolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CashCountTest {

    @Test fun `default denominations - notes and coins, largest first`() {
        assertEquals(listOf(50_000L, 20_000, 10_000, 5_000, 1_000, 500, 250, 100, 50, 10), Denominations.Default)
        assertTrue(Denominations.isNote(1_000))
        assertFalse(Denominations.isNote(500))
    }

    @Test fun `denominations encode, decode, normalise`() {
        assertEquals("50000,1000,500", Denominations.encode(listOf(500, 50_000, 1_000, 500, 0, -5)))
        assertEquals(listOf(50_000L, 1_000, 500), Denominations.decode("500,50000,1000"))
        assertEquals(Denominations.Default, Denominations.decode(null))
        assertEquals(Denominations.Default, Denominations.decode(""))
        assertEquals(Denominations.Default, Denominations.decode("abc,-1"))
    }

    @Test fun `counter total - 2 x 20, 1 x 5, 3 x half, 4 x 10 qirsh, 1 x 1 qirsh`() {
        val s = CountSheet().set(20_000, 2).plus(5_000).set(500, 3).set(100, 4).plus(10)
        assertEquals(40_000L + 5_000 + 1_500 + 400 + 10, s.total)
        assertEquals(11, s.pieces)
        assertEquals(46_910L, s.total)
    }

    @Test fun `counter never goes below zero and removes empty values`() {
        val s = CountSheet().plus(1_000).minus(1_000).minus(1_000)
        assertEquals(0, s.count(1_000))
        assertTrue(s.counts.isEmpty())
        assertEquals(0L, s.total)
    }

    @Test fun `counter json round trip`() {
        val s = CountSheet().set(50_000, 1).set(500, 2).set(10, 7)
        assertEquals("{\"50000\":1,\"500\":2,\"10\":7}", s.toJson())
        assertEquals(s, CountSheet.fromJson(s.toJson()))
        assertEquals(CountSheet(), CountSheet.fromJson(null))
        assertEquals("{}", CountSheet().toJson())
    }

    @Test fun `difference - match, missing, extra`() {
        assertEquals(CountDifference.Match, CountDifference.of(14_650, 14_650))
        assertEquals(CountDifference.Missing(2_500), CountDifference.of(14_650, 12_150))
        assertEquals(CountDifference.Extra(1_000), CountDifference.of(14_650, 15_650))
        assertEquals(CountDifference.Missing(14_650), CountDifference.of(14_650, 0))
        assertEquals(CountDifference.Extra(500), CountDifference.of(-500, 0)) // a negative ledger balance
    }

    @Test fun `missing - part explained as an expense, the rest lost`() {
        var r = Resolution(CountDifference.Missing(2_500))
        assertEquals(2_500L, r.remaining)
        r = r.add(Explanation.Expense(1_500, categoryId = 1, note = "شاورما"))
        assertEquals(1_000L, r.remaining)
        assertFalse(r.complete)
        r = r.rest()
        assertTrue(r.complete)
        assertEquals(listOf(Explanation.Expense(1_500, 1, "شاورما"), Explanation.Lost(1_000)), r.explanations)
    }

    @Test fun `explanations are capped at what is left`() {
        val r = Resolution(CountDifference.Missing(2_500)).add(Explanation.Expense(9_000, 1))
        assertEquals(2_500L, r.explanations.single().amount)
        assertTrue(r.complete)
        assertEquals(r, r.add(Explanation.Lost(100))) // nothing left to explain
        assertEquals(r, r.rest())
    }

    @Test fun `extra - forgotten income or found`() {
        var r = Resolution(CountDifference.Extra(1_000)).add(Explanation.Income(400, 2))
        assertEquals(600L, r.remaining)
        r = r.rest()
        assertEquals(Explanation.Found(600), r.explanations.last())
        assertTrue(r.complete)
    }

    @Test fun `removing an explanation brings its amount back`() {
        val r = Resolution(CountDifference.Missing(2_500)).add(Explanation.Expense(1_500, 1)).rest().removeAt(0)
        assertEquals(listOf(Explanation.Lost(1_000)), r.explanations)
        assertEquals(1_500L, r.remaining)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `an expense cannot explain extra money`() {
        Resolution(CountDifference.Extra(1_000)).add(Explanation.Expense(500, 1))
    }

    @Test fun `a match needs no explanation`() {
        val r = Resolution(CountDifference.Match)
        assertTrue(r.complete)
        assertEquals(r, r.rest())
    }
}
