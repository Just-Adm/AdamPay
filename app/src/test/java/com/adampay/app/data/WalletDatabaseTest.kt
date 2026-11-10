package com.adampay.app.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.adampay.app.data.db.AdamDatabase
import com.adampay.app.data.db.CategoryType
import com.adampay.app.data.db.TxnEntity
import com.adampay.app.data.db.TxnSource
import com.adampay.app.data.db.TxnType
import com.adampay.app.data.wallet.WalletRepository
import com.adampay.app.domain.wallet.Ledger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** The real Room schema and SQL: seeding, the balance query, undo, quick items, archiving. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class WalletDatabaseTest {
    private lateinit var db: AdamDatabase
    private lateinit var repo: WalletRepository
    private val clock = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Asia/Amman"))

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AdamDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = WalletRepository(db, clock)
    }

    @After fun tearDown() = db.close()

    @Test fun `seeding runs once`() = runBlocking {
        repo.ensureSeeded()
        repo.ensureSeeded()
        val s = repo.snapshot.first()
        assertEquals(3, s.pockets.size)
        assertEquals(listOf("pocket", "home", "piggy"), s.pockets.map { it.seedKey })
        assertEquals(8, s.categories(CategoryType.EXPENSE).size)
        assertEquals(5, s.categories(CategoryType.INCOME).size)
        assertEquals(3, s.quickItems.size)
        // Quick items point at real categories.
        assertTrue(s.quickItems.all { q -> s.category(q.categoryId) != null })
        assertEquals(0L, s.total)
    }

    @Test fun `balance query matches the pure ledger`() = runBlocking {
        repo.ensureSeeded()
        val p = repo.snapshot.first().pockets
        val (pocket, home, piggy) = p.map { it.id }
        repo.setOpeningBalances(mapOf(pocket to 20_000L, home to 50_000L, piggy to 0L))
        val t = clock.millis()
        listOf(
            TxnEntity(type = TxnType.EXPENSE, amountFils = 1_500, fromPocketId = pocket, timestamp = t),
            TxnEntity(type = TxnType.INCOME, amountFils = 5_000, toPocketId = pocket, timestamp = t),
            TxnEntity(type = TxnType.TRANSFER, amountFils = 10_000, fromPocketId = home, toPocketId = piggy, timestamp = t),
            TxnEntity(type = TxnType.LOST, amountFils = 2_500, fromPocketId = pocket, timestamp = t),
            TxnEntity(type = TxnType.FOUND, amountFils = 500, toPocketId = home, timestamp = t),
        ).forEach { repo.save(it) }
        val s = repo.snapshot.first()
        val all = repo.txns.first()
        assertEquals(Ledger.balances(all), s.balances)
        assertEquals(21_000L, s.balance(pocket))
        assertEquals(40_500L, s.balance(home))
        assertEquals(10_000L, s.balance(piggy))
        assertEquals(71_500L, s.total)
        // The empty pocket got no opening row.
        assertEquals(2, all.count { it.type == TxnType.OPENING })
    }

    @Test fun `running setup again replaces the opening balance`() = runBlocking {
        repo.ensureSeeded()
        val (pocket, home) = repo.snapshot.first().pockets.map { it.id }
        repo.setOpeningBalances(mapOf(pocket to 20_000L, home to 5_000L))
        repo.save(TxnEntity(type = TxnType.EXPENSE, amountFils = 1_500, fromPocketId = pocket, timestamp = clock.millis()))
        assertEquals(mapOf(pocket to 20_000L, home to 5_000L), repo.openingBalances())
        repo.setOpeningBalances(mapOf(pocket to 30_000L))
        assertEquals(mapOf(pocket to 30_000L), repo.openingBalances())
        val s = repo.snapshot.first()
        assertEquals(28_500L, s.balance(pocket))
        assertEquals(0L, s.balance(home))
    }

    @Test fun `untouched 0_1_0 pockets and categories move to the new look and names`() = runBlocking {
        val dao = db.walletDao()
        dao.upsertPocket(com.adampay.app.data.db.PocketEntity(nameAr = "بجيبتي", nameEn = "In my pocket", icon = "wallet", color = 0xFFE3B25A, color2 = 0xFF8F5E1A, seedKey = "pocket"))
        dao.upsertPocket(com.adampay.app.data.db.PocketEntity(nameAr = "خزنتي", nameEn = "Hidden at home", icon = "cottage", color = 0xFF123456, seedKey = "home"))
        dao.upsertCategory(com.adampay.app.data.db.CategoryEntity(nameAr = "أكل", nameEn = "Food", icon = "lunch_dining", color = 0xFFF2A65A, type = CategoryType.EXPENSE, seedKey = "food"))
        repo.ensureSeeded()
        val s = repo.snapshot.first()
        val pocket = s.pockets.first { it.seedKey == "pocket" }
        assertEquals("في جيبي", pocket.nameAr)
        assertEquals(0xFF2FB36D, pocket.color)
        val home = s.pockets.first { it.seedKey == "home" }
        assertEquals("خزنتي", home.nameAr) // renamed by the user: kept
        assertEquals(0xFF123456, home.color) // recoloured by the user: kept
        assertEquals("طعام", s.categories.first { it.seedKey == "food" }.nameAr)
    }

    @Test fun `cash count - explained expense plus lost brings the balance to the counted cash`() = runBlocking {
        repo.ensureSeeded()
        val s0 = repo.snapshot.first()
        val pocket = s0.pockets.first().id
        val food = s0.categories.first { it.seedKey == "food" }.id
        repo.setOpeningBalances(mapOf(pocket to 14_650L))
        val sheet = com.adampay.app.domain.cash.CountSheet().set(10_000, 1).set(1_000, 2).set(100, 1).plus(50)
        val counted = sheet.total // 12.150
        val r = com.adampay.app.domain.cash.Resolution(com.adampay.app.domain.cash.CountDifference.of(14_650, counted))
            .add(com.adampay.app.domain.cash.Explanation.Expense(1_500, food, "شاورما")).rest()
        val countId = repo.saveCount(pocket, 14_650, counted, sheet, r)
        assertEquals(counted, repo.snapshot.first().balance(pocket))
        val rows = repo.txns.first().filter { it.cashCountId == countId }
        assertEquals(setOf(TxnType.EXPENSE, TxnType.LOST), rows.map { it.type }.toSet())
        assertTrue(rows.all { it.createdBy == TxnSource.COUNT && it.fromPocketId == pocket })
        assertEquals(1_000L, rows.first { it.type == TxnType.LOST }.amountFils)
        val last = repo.latestCounts.first()[pocket]!!
        assertEquals(14_650L, last.expectedFils)
        assertEquals(counted, last.countedFils)
        assertEquals(sheet, com.adampay.app.domain.cash.CountSheet.fromJson(last.denominations))
    }

    @Test fun `cash count - extra money found, and a matching count adds no rows`() = runBlocking {
        repo.ensureSeeded()
        val pocket = repo.snapshot.first().pockets.first().id
        repo.setOpeningBalances(mapOf(pocket to 5_000L))
        val extra = com.adampay.app.domain.cash.Resolution(com.adampay.app.domain.cash.CountDifference.of(5_000, 6_000)).rest()
        repo.saveCount(pocket, 5_000, 6_000, null, extra)
        assertEquals(6_000L, repo.snapshot.first().balance(pocket))
        val before = repo.txns.first().size
        repo.saveCount(pocket, 6_000, 6_000, null, com.adampay.app.domain.cash.Resolution(com.adampay.app.domain.cash.CountDifference.Match))
        assertEquals(before, repo.txns.first().size)
        assertEquals(1, repo.txns.first().count { it.type == TxnType.FOUND })
    }

    @Test fun `transfer moves money without changing the total`() = runBlocking {
        repo.ensureSeeded()
        val (pocket, home, piggy) = repo.snapshot.first().pockets.map { it.id }
        repo.setOpeningBalances(mapOf(home to 50_000L))
        repo.transfer(home, piggy, 10_000, note = "saving")
        val s = repo.snapshot.first()
        assertEquals(40_000L, s.balance(home))
        assertEquals(10_000L, s.balance(piggy))
        assertEquals(50_000L, s.total)
        assertEquals(0L, s.balance(pocket))
    }

    @Test fun `removing pockets - refused with money, archived with history, deleted when unused`() = runBlocking {
        repo.ensureSeeded()
        val s = repo.snapshot.first()
        val (pocket, home, piggy) = s.pockets
        repo.setOpeningBalances(mapOf(home.id to 1_000L))
        assertEquals(com.adampay.app.data.wallet.PocketRemoval.HAS_MONEY, repo.removePocket(home, 1_000))
        repo.transfer(home.id, pocket.id, 1_000)
        assertEquals(com.adampay.app.data.wallet.PocketRemoval.ARCHIVED, repo.removePocket(home, 0))
        assertEquals(com.adampay.app.data.wallet.PocketRemoval.DELETED, repo.removePocket(piggy, 0))
        val after = repo.snapshot.first()
        assertTrue(after.pocket(home.id)!!.archived)
        assertNull(after.pocket(piggy.id))
        assertEquals(1_000L, after.total)
        // The only active pocket left can never be removed, even when empty.
        assertEquals(com.adampay.app.data.wallet.PocketRemoval.LAST_ONE, repo.removePocket(after.pocket(pocket.id)!!, 0))
        repo.restorePocket(after.pocket(home.id)!!)
        assertEquals(2, repo.snapshot.first().activePockets.size)
    }

    @Test fun `new pockets go to the end`() = runBlocking {
        repo.ensureSeeded()
        val id = repo.savePocket(com.adampay.app.data.db.PocketEntity(nameAr = "محفظة", nameEn = "Wallet", icon = "wallet", color = 0xFF2FB36D))
        assertEquals(id, repo.snapshot.first().activePockets.last().id)
    }

    @Test fun `weekly budget - newest limit applies, removing turns it off`() = runBlocking {
        assertNull(repo.budget.first())
        repo.setBudget(7_000, 20_378)
        repo.setBudget(10_000, 20_385)
        assertEquals(10_000L, repo.budget.first()!!.limitFils)
        repo.setBudget(null, 20_385)
        assertNull(repo.budget.first())
    }

    @Test fun `goals - save, adjust manual savings, reorder, delete`() = runBlocking {
        val a = repo.saveGoal(com.adampay.app.data.db.GoalEntity(name = "Headphones", icon = "headphones", color = 0xFF5AA9F2, targetFils = 35_000, createdAt = 0))
        val b = repo.saveGoal(com.adampay.app.data.db.GoalEntity(name = "Football", icon = "sports_soccer", color = 0xFF4FC7A0, targetFils = 12_000, createdAt = 0))
        var goals = repo.goals.first()
        assertEquals(listOf(a, b), goals.map { it.id })
        assertTrue(goals.all { it.createdAt == clock.millis() })
        repo.adjustGoal(goals[0], 5_000)
        repo.adjustGoal(repo.goals.first()[0], -9_000) // never below zero
        assertEquals(0L, repo.goals.first()[0].savedFils)
        repo.reorderGoals(repo.goals.first().reversed())
        assertEquals(listOf(b, a), repo.goals.first().map { it.id })
        repo.deleteGoal(b)
        goals = repo.goals.first()
        assertEquals(listOf(a), goals.map { it.id })
    }

    @Test fun `delete then undo restores the same row`() = runBlocking {
        repo.ensureSeeded()
        val pocket = repo.snapshot.first().pockets.first().id
        val id = repo.save(TxnEntity(type = TxnType.EXPENSE, amountFils = 750, fromPocketId = pocket, note = "Falafel", timestamp = clock.millis()))
        val deleted = repo.delete(id)
        assertNotNull(deleted)
        assertNull(repo.txn(id))
        assertEquals(0L, repo.snapshot.first().balance(pocket))
        repo.restore(deleted!!)
        assertEquals(deleted, repo.txn(id))
        assertEquals(-750L, repo.snapshot.first().balance(pocket))
    }

    @Test fun `editing keeps the id`() = runBlocking {
        repo.ensureSeeded()
        val pocket = repo.snapshot.first().pockets.first().id
        val id = repo.save(TxnEntity(type = TxnType.EXPENSE, amountFils = 750, fromPocketId = pocket, timestamp = clock.millis()))
        val row = repo.txn(id)!!
        assertEquals(id, repo.save(row.copy(amountFils = 1_000, note = "edited")))
        assertEquals(1, repo.txns.first().size)
        assertEquals(1_000L, repo.txn(id)!!.amountFils)
    }

    @Test fun `quick item logs one expense in the fallback pocket`() = runBlocking {
        repo.ensureSeeded()
        val s = repo.snapshot.first()
        val q = s.quickItems.first()
        val row = repo.logQuick(q, s.pockets.first().id, note = "Service taxi")
        assertEquals(TxnType.EXPENSE, row.type)
        assertEquals(TxnSource.QUICK, row.createdBy)
        assertEquals(q.amountFils, row.amountFils)
        assertEquals("Service taxi", row.note)
        assertEquals(-q.amountFils, repo.snapshot.first().balance(s.pockets.first().id))
    }

    @Test fun `used categories are archived, unused deleted`() = runBlocking {
        repo.ensureSeeded()
        val s = repo.snapshot.first()
        val food = s.categories.first { it.seedKey == "food" }
        val games = s.categories.first { it.seedKey == "games" }
        repo.save(TxnEntity(type = TxnType.EXPENSE, amountFils = 1_000, categoryId = food.id, fromPocketId = s.pockets.first().id, timestamp = clock.millis()))
        repo.removeCategory(food)
        repo.removeCategory(games)
        val after = repo.snapshot.first()
        assertTrue(after.category(food.id)!!.archived)
        assertNull(after.category(games.id))
        assertTrue(after.categories(CategoryType.EXPENSE).none { it.id == food.id })
    }

    @Test fun `reorder categories and quick items`() = runBlocking {
        repo.ensureSeeded()
        val s = repo.snapshot.first()
        val reversed = s.categories(CategoryType.EXPENSE).reversed()
        repo.reorderCategories(reversed)
        assertEquals(reversed.map { it.id }, repo.snapshot.first().categories(CategoryType.EXPENSE).map { it.id })
        val q = s.quickItems.reversed()
        repo.reorderQuickItems(q)
        assertEquals(q.map { it.id }, repo.snapshot.first().quickItems.map { it.id })
    }
}
