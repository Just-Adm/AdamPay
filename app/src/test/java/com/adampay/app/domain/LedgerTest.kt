package com.adampay.app.domain

import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.db.TxnType
import com.adampay.app.domain.layout.BoxId
import com.adampay.app.domain.layout.PageId
import com.adampay.app.domain.layout.PageLayout
import com.adampay.app.domain.model.AppLanguage
import com.adampay.app.domain.wallet.BiText
import com.adampay.app.domain.wallet.HistoryFilter
import com.adampay.app.domain.wallet.Ledger
import com.adampay.app.domain.wallet.MoneyFlow
import com.adampay.app.domain.wallet.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class LedgerTest {
    private val zone = ZoneId.of("Asia/Amman")
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 10, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val POCKET = 1L
    private val HOME = 2L
    private val PIGGY = 3L
    private val FOOD = 10L
    private val TRANSPORT = 11L

    private val rows = listOf(
        TxnEntity(id = 1, type = TxnType.OPENING, amountFils = 20_000, toPocketId = POCKET, timestamp = at(15, 9)),
        TxnEntity(id = 2, type = TxnType.OPENING, amountFils = 50_000, toPocketId = HOME, timestamp = at(15, 9)),
        TxnEntity(id = 3, type = TxnType.EXPENSE, amountFils = 1_500, categoryId = FOOD, fromPocketId = POCKET, note = "شاورما", timestamp = at(16, 13)),
        TxnEntity(id = 4, type = TxnType.EXPENSE, amountFils = 350, categoryId = TRANSPORT, fromPocketId = POCKET, note = "Service", timestamp = at(16, 8)),
        TxnEntity(id = 5, type = TxnType.INCOME, amountFils = 5_000, categoryId = 20, toPocketId = POCKET, timestamp = at(17, 10)),
        TxnEntity(id = 6, type = TxnType.TRANSFER, amountFils = 10_000, fromPocketId = HOME, toPocketId = PIGGY, timestamp = at(17, 11)),
        TxnEntity(id = 7, type = TxnType.LOST, amountFils = 2_500, fromPocketId = POCKET, timestamp = at(17, 21)),
        TxnEntity(id = 8, type = TxnType.FOUND, amountFils = 500, toPocketId = HOME, timestamp = at(17, 21)),
    )

    @Test fun `balances - in minus out per pocket`() {
        val b = Ledger.balances(rows)
        assertEquals(20_000L - 1_500 - 350 + 5_000 - 2_500, b[POCKET])
        assertEquals(50_000L - 10_000 + 500, b[HOME])
        assertEquals(10_000L, b[PIGGY])
    }

    @Test fun `transfers don't change the total`() {
        val withTransfer = Ledger.total(Ledger.balances(rows))
        val without = Ledger.total(Ledger.balances(rows.filterNot { it.type == TxnType.TRANSFER }))
        assertEquals(without, withTransfer)
        assertEquals(20_000L + 50_000 - 1_500 - 350 + 5_000 - 2_500 + 500, withTransfer)
    }

    @Test fun `spent, earned, lost are separate`() {
        assertEquals(1_850L, Ledger.spent(rows))
        assertEquals(5_000L, Ledger.earned(rows)) // OPENING is never income
        assertEquals(2_500L, Ledger.lost(rows))
    }

    @Test fun `flow of each type`() {
        assertEquals(MoneyFlow.OUT, TxnType.EXPENSE.flow)
        assertEquals(MoneyFlow.OUT, TxnType.LOST.flow)
        assertEquals(MoneyFlow.IN, TxnType.INCOME.flow)
        assertEquals(MoneyFlow.IN, TxnType.FOUND.flow)
        assertEquals(MoneyFlow.IN, TxnType.OPENING.flow)
        assertEquals(MoneyFlow.NEUTRAL, TxnType.TRANSFER.flow)
    }

    @Test fun `grouped by day, newest first, with day totals`() {
        val sorted = rows.sortedByDescending { it.timestamp }
        val g = Ledger.byDay(sorted, zone)
        assertEquals(listOf(LocalDate.of(2026, 10, 17), LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 15)), g.map { it.day })
        assertEquals(2_500L, g[0].out) // lost counts as money out
        assertEquals(5_500L, g[0].`in`) // income + found, not the transfer
        assertEquals(1_850L, g[1].out)
        assertEquals(0L, g[2].`in`) // opening cash is not "in"
        assertEquals(listOf(3L, 4L), g[1].rows.map { it.id })
    }

    @Test fun `day boundaries use the phone's time zone`() {
        val lateNight = LocalDateTime.of(2026, 10, 16, 23, 59).atZone(zone).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 10, 16), Ledger.date(lateNight, zone))
        assertEquals(LocalDate.of(2026, 10, 17), Ledger.date(lateNight + 60_000, zone))
        assertEquals(at(17, 0), Ledger.startOfDay(LocalDate.of(2026, 10, 17), zone))
    }

    @Test fun `history filter - kinds, pocket, category`() {
        assertEquals(8, HistoryFilter().apply(rows).size)
        assertEquals(listOf(3L, 4L), HistoryFilter(kind = HistoryFilter.Kind.EXPENSES).apply(rows).map { it.id })
        assertEquals(listOf(5L), HistoryFilter(kind = HistoryFilter.Kind.INCOME).apply(rows).map { it.id })
        assertEquals(listOf(6L), HistoryFilter(kind = HistoryFilter.Kind.TRANSFERS).apply(rows).map { it.id })
        assertEquals(listOf(7L, 8L), HistoryFilter(kind = HistoryFilter.Kind.LOST_FOUND).apply(rows).map { it.id })
        // A transfer shows under both of its pockets.
        assertEquals(listOf(2L, 6L, 8L), HistoryFilter(pocketId = HOME).apply(rows).map { it.id })
        assertEquals(listOf(6L), HistoryFilter(pocketId = PIGGY).apply(rows).map { it.id })
        assertEquals(listOf(3L), HistoryFilter(categoryId = FOOD).apply(rows).map { it.id })
    }

    @Test fun `history search - notes and category names, Arabic-aware`() {
        val names = mapOf(FOOD to listOf("أكل", "Food"), TRANSPORT to listOf("مواصلات", "Transport"))
        assertEquals(listOf(3L), HistoryFilter(query = "شاورما").apply(rows, names).map { it.id })
        assertEquals(listOf(4L), HistoryFilter(query = "service").apply(rows, names).map { it.id })
        assertEquals(listOf(3L), HistoryFilter(query = "food").apply(rows, names).map { it.id })
        assertEquals(listOf(3L), HistoryFilter(query = "اكل").apply(rows, names).map { it.id }) // أ written as ا
        assertEquals(listOf(4L), HistoryFilter(query = "  TRANS ").apply(rows, names).map { it.id })
        assertTrue(HistoryFilter(query = "pizza").apply(rows, names).isEmpty())
        assertTrue(HistoryFilter().isDefault)
        assertFalse(HistoryFilter(pocketId = 1).isDefault)
    }

    @Test fun `names follow the language mode`() {
        val t = BiText("أكل", "Food")
        assertEquals("Food", t.forTerms(AppLanguage.MIXED))
        assertEquals("أكل", t.forTerms(AppLanguage.ARABIC))
        assertEquals("Food", t.forTerms(AppLanguage.ENGLISH))
        assertEquals("أكل", t.forUi(AppLanguage.MIXED))
        assertEquals("Food", t.forUi(AppLanguage.ENGLISH))
        assertEquals("Food", BiText("", "Food").forTerms(AppLanguage.ARABIC)) // blank side falls back
    }

    @Test fun `home layout - move, hide, encode, decode`() {
        val l = PageLayout.default(PageId.HOME).move(BoxId.HOME_RECENT, BoxId.HOME_FEATURED).toggle(BoxId.HOME_QUICK)
        assertEquals(BoxId.HOME_RECENT, l.order.first())
        assertFalse(BoxId.HOME_QUICK in l.visible)
        assertEquals(l, PageLayout.decode(PageId.HOME, l.encode()))
        // Unknown names are dropped; missing sections come back at their default place.
        val restored = PageLayout.decode(PageId.HOME, "HOME_TODAY,NOT_A_BOX|")
        assertEquals(BoxId.defaults(PageId.HOME).toSet(), restored.order.toSet())
        assertEquals(PageLayout.default(PageId.HOME), PageLayout.decode(PageId.HOME, null))
    }
}
