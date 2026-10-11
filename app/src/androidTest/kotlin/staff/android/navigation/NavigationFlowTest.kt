package staff.android.navigation

import android.content.Intent
import android.net.Uri
import android.view.View
import androidx.activity.BackEventCompat
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.matcher.ViewMatchers.withId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

class FakeCartRepository : CartRepository {
    override val records = MutableStateFlow(Products.all.associate { it.id to AcceptedQuantity() })
    var calls = 0
    var fail = false
    var gate: CompletableDeferred<Unit>? = null
    override suspend fun confirm(id: String, quantity: Int, revision: Long) {
        calls++
        gate?.await()
        if (fail) throw IOException("Controlled storage failure")
        val old = records.value.getValue(id)
        if (old.revision != revision) throw RevisionMismatch()
        if (old.quantity != quantity) records.value = records.value +
            (id to AcceptedQuantity(quantity, old.revision + 1))
    }
    override suspend fun reset() {
        records.value = Products.all.associate { it.id to AcceptedQuantity() }
    }
}
class NavigationFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val repository = FakeCartRepository()
    @Before fun configure() {
        compose.activityRule.scenario.onActivity {
            (it.application as ShopApplication).apply {
                this.repository = this@NavigationFlowTest.repository
                composeEditor = true
                brokenReturn = false
            }
        }
    }
    private fun current(): Int {
        var id = 0
        compose.activityRule.scenario.onActivity { id = it.navigation.currentDestination!!.id }
        return id
    }
    private fun at(id: Int) {
        compose.waitUntil(8_000) { current() == id }
        compose.waitForIdle()
    }
    private fun editor(activity: MainActivity): QuantityFragment =
        (activity.supportFragmentManager.findFragmentById(R.id.nav_host) as NavHostFragment)
            .childFragmentManager.primaryNavigationFragment as QuantityFragment
    private fun open(composeRenderer: Boolean = true, broken: Boolean = false) {
        compose.activityRule.scenario.onActivity {
            (it.application as ShopApplication).apply { composeEditor = composeRenderer; brokenReturn = broken }
        }
        onView(withId(R.id.open_cart)).perform(click())
        at(R.id.cart)
        onView(withId(R.id.open_camera)).perform(click())
        at(R.id.quantity)
        if (composeRenderer) compose.onNodeWithTag("quantity_input").assertExists()
    }
    private fun back() { compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() } }
    @Test fun confirmReturnsToExistingCartThenBackReachesCatalog() {
        open()
        compose.onNodeWithTag("quantity_input").performTextReplacement("3")
        compose.onNodeWithTag("confirm").performClick()
        at(R.id.cart)
        assertEquals(3, repository.records.value.getValue("camera").quantity)
        compose.activityRule.scenario.onActivity {
            assertEquals(R.id.catalog, it.navigation.previousBackStackEntry!!.destination.id)
        }
        back(); at(R.id.catalog)
        assertEquals(1, repository.calls)
    }
    @Test fun brokenAppendLeavesFinishedEditorUnderNewCart() {
        open(broken = true)
        compose.onNodeWithTag("quantity_input").performTextReplacement("3")
        compose.onNodeWithTag("confirm").performClick()
        at(R.id.cart)
        compose.activityRule.scenario.onActivity {
            assertEquals(R.id.quantity, it.navigation.previousBackStackEntry!!.destination.id)
        }
        back(); at(R.id.quantity)
        compose.onNodeWithText("Saved. This editor entry has finished.").assertExists()
        assertEquals(1, repository.calls)
    }
    @Test fun legacyRendererPreservesTheSameSaveAndReturnContract() {
        open(composeRenderer = false)
        onView(withId(R.id.quantity_input)).perform(replaceText("3"))
        onView(withId(R.id.confirm)).perform(click())
        at(R.id.cart)
        assertEquals(3, repository.records.value.getValue("camera").quantity)
        back(); at(R.id.catalog)
    }
    @Test fun backBeforeConfirmAbandonsOnlyThisEntryDraft() {
        open()
        compose.onNodeWithTag("quantity_input").performTextReplacement("3")
        back(); at(R.id.cart)
        onView(withId(R.id.open_camera)).perform(click()); at(R.id.quantity)
        compose.onNodeWithTag("quantity_input").assertTextContains("2")
        assertEquals(0, repository.calls)
    }
    @Test fun cancelledPredictiveBackPreservesDraftCommittedBackPops() {
        open()
        compose.onNodeWithTag("quantity_input").performTextReplacement("3")
        compose.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        compose.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(70f, 100f, .5f, BackEventCompat.EDGE_LEFT))
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        compose.activityRule.scenario.onActivity {
            it.onBackPressedDispatcher.dispatchOnBackCancelled()
        }
        at(R.id.quantity)
        compose.waitUntil(8_000) {
            compose.onAllNodesWithTag("quantity_input").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("quantity_input").assertTextContains("3")
        back(); at(R.id.cart)
        assertEquals(0, repository.calls)
    }
    @Test fun recreationRetainsEntryOwnerAndDisposesOldComposition() {
        open()
        compose.onNodeWithTag("quantity_input").performTextReplacement("3")
        lateinit var oldOwner: QuantityViewModel
        lateinit var oldFragment: Fragment
        lateinit var oldView: ComposeView
        lateinit var oldLifecycle: Lifecycle
        var entryId = ""
        compose.activityRule.scenario.onActivity {
            oldFragment = editor(it); oldOwner = editor(it).owner
            oldView = oldFragment.requireView() as ComposeView
            oldLifecycle = oldFragment.viewLifecycleOwner.lifecycle
            entryId = it.navigation.currentBackStackEntry!!.id
        }
        compose.activityRule.scenario.recreate()
        at(R.id.quantity)
        compose.activityRule.scenario.onActivity {
            assertEquals(entryId, it.navigation.currentBackStackEntry!!.id)
            assertSame(oldOwner, editor(it).owner)
            assertNotSame(oldFragment, editor(it))
            assertEquals(Lifecycle.State.DESTROYED, oldLifecycle.currentState)
            assertFalse(oldView.hasComposition)
        }
        compose.onNodeWithTag("quantity_input").assertTextContains("3")
        assertEquals(2, repository.records.value.getValue("camera").quantity)
    }
    @Test fun successfulSaveDuringRecreationReturnsOnlyOnce() {
        repository.gate = CompletableDeferred()
        open()
        compose.onNodeWithTag("quantity_input").performTextReplacement("3")
        compose.onNodeWithTag("confirm").performClick()
        compose.activityRule.scenario.recreate()
        at(R.id.quantity)
        repository.gate!!.complete(Unit)
        at(R.id.cart)
        back(); at(R.id.catalog)
        assertEquals(1, repository.calls)
    }
    @Test fun failureKeepsTheEditorAndRetryIsExplicit() {
        repository.fail = true
        open()
        compose.onNodeWithTag("quantity_input").performTextReplacement("3")
        compose.onNodeWithTag("confirm").performClick()
        compose.onNodeWithTag("problem").assertTextContains("Could not save.", substring = true)
        assertEquals(R.id.quantity, current())
        compose.onNodeWithTag("quantity_input").assertTextContains("3")
        repository.fail = false
        compose.onNodeWithTag("confirm").performClick()
        at(R.id.cart)
        assertEquals(2, repository.calls)
    }
    @Test fun deepLinkWithNewTaskBuildsDeclaredParentStarts() {
        compose.activityRule.scenario.onActivity {
            assertTrue(it.navigation.handleDeepLink(Intent(Intent.ACTION_VIEW,
                Uri.parse("shoplab://cart/edit/tea")).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)))
        }
        at(R.id.quantity)
        compose.onNodeWithText("Tea").assertExists()
        back(); at(R.id.cart)
        back(); at(R.id.catalog)
        assertEquals(0, repository.calls)
    }
    @Test fun unknownDeepLinkProductNeverEnablesAWrite() {
        compose.activityRule.scenario.onActivity {
            it.navigation.handleDeepLink(Intent(Intent.ACTION_VIEW,
                Uri.parse("shoplab://cart/edit/not-in-catalog")).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }
        at(R.id.quantity)
        compose.onAllNodesWithText("This product is unavailable.").assertCountEquals(2)
        compose.onNodeWithTag("confirm").assertDoesNotExist()
        assertEquals(0, repository.calls)
    }
    @Test fun rendererChoiceIsEntryArgumentAcrossRecreation() {
        open(composeRenderer = false)
        onView(withId(R.id.quantity_input)).perform(replaceText("3"))
        compose.activityRule.scenario.onActivity { (it.application as ShopApplication).composeEditor = true }
        compose.activityRule.scenario.recreate()
        at(R.id.quantity)
        compose.activityRule.scenario.onActivity {
            assertNotNull(it.findViewById<View>(R.id.quantity_legacy))
            assertNull(it.findViewById<View>(R.id.quantity_compose))
        }
    }
    @Test fun legacyRejectsTheWholeOverlongReplacementBeforeItBecomesVisible() {
        open(composeRenderer = false)
        onView(withId(R.id.quantity_input)).perform(replaceText("3"))
        compose.activityRule.scenario.onActivity {
            val field = it.findViewById<android.widget.EditText>(R.id.quantity_input)
            // Model replacement in the existing Editable (typing/paste), not setText's new buffer.
            field.text.replace(0, field.text.length, "0".repeat(32) + "4")
            assertEquals("3", field.text.toString())
            assertEquals("3", editor(it).owner.state.value.raw)
        }
    }
    @Test fun legacyPreservesInvalidRawInputForSharedValidation() {
        open(composeRenderer = false)
        for (raw in listOf("-1", "3x", " 3 ")) {
            compose.activityRule.scenario.onActivity {
                val field = it.findViewById<android.widget.EditText>(R.id.quantity_input)
                field.text.replace(0, field.text.length, raw)
                assertEquals(raw, field.text.toString())
                assertEquals(raw, editor(it).owner.state.value.raw)
                assertEquals(raw == " 3 ", editor(it).owner.state.value.canConfirm)
            }
        }
        assertEquals(0, repository.calls)
    }
    @Test fun composePreservesTheSameRawInputValidationPolicy() {
        open()
        for (raw in listOf("-1", "3x", " 3 ")) {
            compose.onNodeWithTag("quantity_input").performTextReplacement(raw)
            compose.onNodeWithTag("quantity_input").assertTextContains(raw)
            if (raw == " 3 ") compose.onNodeWithTag("confirm").assertIsEnabled()
            else compose.onNodeWithTag("confirm").assertIsNotEnabled()
        }
        assertEquals(0, repository.calls)
    }
    @Test fun completionCannotPopADifferentCurrentEntry() {
        repository.gate = CompletableDeferred()
        open()
        compose.onNodeWithTag("quantity_input").performTextReplacement("3")
        compose.onNodeWithTag("confirm").performClick()
        lateinit var owner: QuantityViewModel
        compose.activityRule.scenario.onActivity {
            owner = editor(it).owner
            it.navigation.navigate(R.id.cart)
        }
        at(R.id.cart)
        repository.gate!!.complete(Unit)
        compose.waitUntil { owner.state.value.phase == SavePhase.Saved }
        assertEquals(R.id.cart, current())
        compose.activityRule.scenario.onActivity {
            assertEquals(R.id.quantity, it.navigation.previousBackStackEntry!!.destination.id)
        }
        back()
        at(R.id.cart)
        compose.activityRule.scenario.onActivity {
            assertEquals(R.id.catalog, it.navigation.previousBackStackEntry!!.destination.id)
        }
        assertEquals(1, repository.calls)
    }
}
