package com.adampay.app.domain

import com.adampay.app.domain.money.AmountInput
import com.adampay.app.domain.money.CurrencyLabel
import com.adampay.app.domain.money.Money
import com.adampay.app.domain.money.MoneyFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test fun `units - dinar, qirsh, fils`() {
        assertEquals(1000L, Money.dinars(1))
        assertEquals(10L, Money.qirsh(1))
        assertEquals(350L, Money.qirsh(35))
        assertEquals(12 to 500, Money.split(12_500).let { it.first.toInt() to it.second.toInt() })
        assertEquals(0 to 5, Money.split(5).let { it.first.toInt() to it.second.toInt() })
    }

    @Test fun `format - three decimals by default`() {
        assertEquals("12.500", Money.formatNumber(12_500))
        assertEquals("0.350", Money.formatNumber(350))
        assertEquals("0.005", Money.formatNumber(5))
        assertEquals("0.000", Money.formatNumber(0))
        assertEquals("1,250.000", Money.formatNumber(1_250_000))
        assertEquals("999,999.999", Money.formatNumber(Money.MAX_FILS))
        assertEquals("1250.000", Money.formatNumber(1_250_000, grouping = false))
    }

    @Test fun `format - negative uses a real minus sign`() {
        assertEquals("−2.500", Money.formatNumber(-2_500))
        assertEquals("−1,000.000", Money.formatNumber(-1_000_000))
    }

    @Test fun `format - two decimals never hides single fils`() {
        assertEquals("12.50", Money.formatNumber(12_500, decimals = 2))
        assertEquals("0.35", Money.formatNumber(350, decimals = 2))
        assertEquals("0.355", Money.formatNumber(355, decimals = 2))
        assertEquals("1.005", Money.formatNumber(1_005, decimals = 2))
        assertEquals("−0.10", Money.formatNumber(-100, decimals = 2))
    }

    @Test fun `format helper - signed and label`() {
        val f = MoneyFormat(3, CurrencyLabel.JD)
        assertEquals("+5.000", f.number(5_000, signed = true))
        assertEquals("5.000", f.number(5_000))
        assertEquals("JD", f.label.text)
        assertEquals("د.أ", CurrencyLabel.ARABIC.text)
    }

    @Test fun `parse - typed amounts`() {
        assertEquals(12_000L, Money.parse("12"))
        assertEquals(12_500L, Money.parse("12.5"))
        assertEquals(12_500L, Money.parse("12.50"))
        assertEquals(350L, Money.parse("0.35"))
        assertEquals(350L, Money.parse(".35"))
        assertEquals(5L, Money.parse("0.005"))
        assertEquals(1_250_750L, Money.parse("1,250.75"))
        assertEquals(1_500L, Money.parse(" 1.5 "))
        assertEquals(2_500L, Money.parse("٢٫٥"), ) // Arabic-Indic digits and decimal separator
        assertEquals(0L, Money.parse("0"))
    }

    @Test fun `parse - rejects bad input`() {
        assertNull(Money.parse(""))
        assertNull(Money.parse("."))
        assertNull(Money.parse("1.2345"))
        assertNull(Money.parse("1.2.3"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("1000000"))
    }

    @Test fun `parse and format round trip`() {
        listOf(0L, 5L, 350L, 1_000L, 12_500L, 999_999_999L).forEach { f ->
            assertEquals(f, Money.parse(Money.formatNumber(f)))
        }
    }

    @Test fun `per day rounds down to whole fils`() {
        assertEquals(1_333L, Money.perDay(4_000, 3))
        assertEquals(0L, Money.perDay(4_000, 0))
        assertTrue(Money.perDay(4_000, 3) * 3 <= 4_000)
    }

    // Keypad
    @Test fun `keypad - typing whole and decimals`() {
        var a = AmountInput()
        a = a.digit(1).dot().digit(5)
        assertEquals(1_500L, a.fils)
        assertEquals("1.5", a.text())
        a = a.digit(0).digit(0)
        assertEquals("1.500", a.text())
        assertEquals(a, a.digit(7)) // max 3 decimals
    }

    @Test fun `keypad - leading zeros, dot first, max digits`() {
        var a = AmountInput().digit(0).digit(0)
        assertTrue(a.isEmpty)
        a = a.dot().digit(3).digit(5)
        assertEquals(350L, a.fils)
        assertEquals("0.35", a.text())
        var big = AmountInput()
        repeat(8) { big = big.digit(9) }
        assertEquals("999,999", big.text())
        assertEquals(999_999_000L, big.fils)
        assertEquals(big, big.dot().dot().backspace()) // second dot ignored, backspace removes the dot
    }

    @Test fun `keypad - backspace walks back`() {
        var a = AmountInput().digit(2).dot().digit(5)
        a = a.backspace(); assertEquals("2.", a.text())
        a = a.backspace(); assertEquals("2", a.text())
        a = a.backspace(); assertTrue(a.isEmpty)
        assertEquals("0", a.text())
        assertEquals(a, a.backspace())
    }

    @Test fun `keypad - prefill from fils`() {
        assertEquals("1.5", AmountInput.of(1_500).text())
        assertEquals("2", AmountInput.of(2_000).text())
        assertEquals("0.35", AmountInput.of(350).text())
        assertEquals("0.005", AmountInput.of(5).text())
        assertTrue(AmountInput.of(0).isEmpty)
        listOf(5L, 350L, 1_500L, 12_345L, 999_999_999L).forEach { assertEquals(it, AmountInput.of(it).fils) }
    }
}
