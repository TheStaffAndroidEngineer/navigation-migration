package staff.android.navigation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class OwnerStoreTest {
    private fun main(block: () -> Unit) = InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
    private fun await(condition: () -> Boolean) {
        val end = System.nanoTime() + 5_000_000_000
        while (!condition() && System.nanoTime() < end) Thread.sleep(5)
        assertTrue("Condition not reached", condition())
    }
    private fun withOwner(repository: CartRepository, saved: SavedStateHandle =
        SavedStateHandle(mapOf("productId" to "camera")), action: (QuantityViewModel) -> Unit) {
        lateinit var owner: QuantityViewModel
        lateinit var store: ViewModelStore
        main { owner = QuantityViewModel(saved, repository); store = ViewModelStore(); store.put("editor", owner) }
        try { action(owner) } finally { main { store.clear() } }
    }
    @Test fun emptyRestoredDraftRemainsUnacceptedAndInvalid() {
        val repo = FakeCartRepository()
        withOwner(repo, SavedStateHandle(mapOf("productId" to "camera", "raw" to "", "baseRevision" to 0L))) {
            await { it.state.value.accepted != null }
            assertEquals("", it.state.value.raw)
            assertFalse(it.state.value.canConfirm)
            main { it.confirm() }
            assertEquals(0, repo.calls)
        }
    }
    @Test fun restoredDraftRevalidatesCurrentAcceptedRevisionWithoutReplay() {
        val repo = FakeCartRepository()
        repo.records.value = repo.records.value + ("camera" to AcceptedQuantity(4, 1))
        withOwner(repo, SavedStateHandle(mapOf("productId" to "camera", "raw" to "3", "baseRevision" to 0L))) {
            await { it.state.value.accepted != null }
            assertTrue(it.state.value.stale)
            assertFalse(it.state.value.canConfirm)
            assertEquals(SavePhase.Editing, it.state.value.phase)
            main { it.confirm() }
            assertEquals(0, repo.calls)
            main { it.reviewCurrent() }
            assertEquals("4", it.state.value.raw)
            assertTrue(it.state.value.canConfirm)
        }
    }
    @Test fun pendingConfirmIsSingleFlightAndUsesTheVisibleTrimmedInput() {
        val repo = FakeCartRepository().apply { gate = CompletableDeferred() }
        withOwner(repo) {
            await { it.state.value.accepted != null }
            main { it.change(" 3 "); it.confirm(); it.confirm(); it.change("5") }
            assertEquals(1, repo.calls)
            assertEquals(" 3 ", it.state.value.raw)
            repo.gate!!.complete(Unit)
            await { it.state.value.phase == SavePhase.Saved }
            assertEquals(3, repo.records.value.getValue("camera").quantity)
        }
    }
    @Test fun overlongInputDoesNotSilentlyAcceptATruncatedPrefix() {
        withOwner(FakeCartRepository()) {
            await { it.state.value.accepted != null }
            main { it.change("3"); it.change("0".repeat(32) + "4") }
            assertEquals("3", it.state.value.raw)
            assertEquals(3, it.state.value.candidate)
        }
    }
    @Test fun readFailureRemovesWriteAuthorityButRetainsRawDraft() {
        val failure = CompletableDeferred<Unit>()
        val repo = object : CartRepository {
            override val records: Flow<Map<String, AcceptedQuantity>> = flow {
                emit(Products.all.associate { it.id to AcceptedQuantity() })
                failure.await()
                throw IOException("Controlled read failure")
            }
            override suspend fun confirm(id: String, quantity: Int, revision: Long) = error("Must not write")
            override suspend fun reset() = Unit
        }
        withOwner(repo) {
            await { it.state.value.accepted != null }
            main { it.change("3") }
            failure.complete(Unit)
            await { it.state.value.problem == EditorProblem.Load }
            assertEquals("3", it.state.value.raw)
            assertNull(it.state.value.accepted)
            assertFalse(it.state.value.canConfirm)
            main { it.confirm() }
        }
    }
    @Test fun unknownIdIsNotARepositoryKey() {
        val repo = FakeCartRepository()
        withOwner(repo, SavedStateHandle(mapOf("productId" to "unknown"))) {
            assertEquals(EditorProblem.UnknownProduct, it.state.value.problem)
            main { it.confirm() }
            assertEquals(0, repo.calls)
        }
    }
    @Test fun actualDataStoreCommitAndRevisionConflictArePerProduct() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val repo = DataStoreCartRepository(context)
        repo.reset()
        repo.confirm("camera", 3, 0)
        assertEquals(AcceptedQuantity(3, 1), repo.records.first().getValue("camera"))
        assertEquals(AcceptedQuantity(2, 0), repo.records.first().getValue("tea"))
        assertThrows(RevisionMismatch::class.java) { runBlocking { repo.confirm("camera", 5, 0) } }
        val reopened = DataStoreCartRepository(context)
        assertEquals(AcceptedQuantity(3, 1), reopened.records.first().getValue("camera"))
        repo.reset()
    }
}
