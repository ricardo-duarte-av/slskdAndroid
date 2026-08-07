package com.slskdandroid

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.slskdandroid.core.designsystem.testing.SlskdTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives the real app end-to-end on an emulator — connect → search → every tab — and captures a
 * full-window PNG of each screen. Run by the `screenshots` workflow (workflow_dispatch), which
 * pulls the PNGs off the device, commits them under `screenshots/`, and refreshes the README
 * gallery from them.
 *
 * It talks to a **real slskd instance over the real Soulseek network**, so it needs internet and a
 * reachable server. Nothing is hard-coded: the connection comes from instrumentation arguments.
 *
 *   -e serverUrl https://…
 *   -e apiKey    …
 *   -e query     'zelda flac'
 *   -e room      slskd
 *
 * Each screen is captured inside its own [capture] block: one content-dependent step going wrong
 * (a peer that went offline, a room with no traffic) logs and is skipped rather than failing the
 * whole run, so we still commit the screens that did render.
 *
 * **Side effects on the target server.** Two, both deliberate: it starts a search (which shows up
 * in the server's search history, and is what the Search screenshots are of), and if the app hasn't
 * joined [room] it joins it, so the room chat has something to render. It never queues a download,
 * sends a message, or deletes anything — so Downloads/Uploads capture whatever transfer state the
 * server already has, empty state included.
 *
 * **Why full-device capture and not `onRoot().captureToImage()`.** Dialogs, dropdown menus and
 * modal sheets each live in their own window, so a Compose-root capture would miss them, and the
 * status/navigation bars — which a README screenshot wants — are outside Compose entirely.
 * [android.app.UiAutomation.takeScreenshot] grabs the composited display instead.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // Pre-grant so that if notifications ever default to on, the POST_NOTIFICATIONS launcher on
    // API 33+ returns without a system dialog sitting over the UI for every capture.
    @get:Rule
    val notifPermission: GrantPermissionRule =
        GrantPermissionRule.grant("android.permission.POST_NOTIFICATIONS")

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val args = InstrumentationRegistry.getArguments()

    private val serverUrl: String = args.getString("serverUrl").orEmpty()
    private val apiKey: String = args.getString("apiKey").orEmpty()

    /** A term that reliably returns results from live peers. See the workflow's `query` input. */
    private val query: String = args.getString("query") ?: "zelda flac"

    /** The room opened for the room-chat shot; joined first if we aren't a member. */
    private val room: String = args.getString("room") ?: "slskd"

    private val outputDir: File by lazy {
        // Internal storage. Under scoped storage adb can't read another app's
        // /sdcard/Android/data/<pkg>, so the workflow pulls these as root instead.
        File(instrumentation.targetContext.filesDir, "screenshots").apply { mkdirs() }
    }

    @Test
    fun captureScreens() {
        check(serverUrl.isNotBlank()) { "serverUrl instrumentation argument is required" }
        check(apiKey.isNotBlank()) { "apiKey instrumentation argument is required" }

        connect()
        val searchOk = runSearch()
        captureTransferTabs()
        captureRooms()
        captureChat()
        if (searchOk) capturePeerTabs()
        captureSettings()

        val produced = outputDir.listFiles()?.map { it.name }?.sorted().orEmpty()
        Log.i(TAG, "captured ${produced.size} screenshots: $produced")
    }

    // --- Onboarding ---------------------------------------------------------------------------

    /**
     * First run lands on the mandatory connection setup (MainViewModel gates the start
     * destination). Two text fields, in order: base URL then API key.
     */
    private fun connect() {
        waitForText("Connect to slskd")
        val fields = composeRule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput(serverUrl)
        fields[1].performTextInput(apiKey)
        // Drop the keyboard so the shot shows the form, not half a screen of keys. The API key
        // field uses PasswordVisualTransformation, so the key itself is never legible in the PNG.
        closeKeyboard()
        capture("01-connect") { /* already in position */ }

        // "Connect to slskd" is the title; an exact-text match hits only the button.
        composeRule.onNodeWithText("Connect").performClick()
        // The shell has taken over once Search's query field renders.
        waitForText(SEARCH_FIELD_LABEL, timeoutMillis = 60_000)
    }

    // --- Search -------------------------------------------------------------------------------

    /**
     * Starts a search, which auto-navigates to the detail screen (SearchListViewModel emits the new
     * id on `openSearch`), captures the streaming results, then backs out to capture the list —
     * which is only worth a screenshot once it has that search in it.
     *
     * @return whether peer results arrived; the Users/Browse shots are driven off them.
     */
    private fun runSearch(): Boolean {
        var gotResults = false

        capture("03-search-results") {
            // The search list has exactly one text field.
            composeRule.onNode(hasSetTextAction()).performTextInput(query)
            closeKeyboard()
            // ImeAction.Search → SearchListAction.Submit, the same path as tapping the field's
            // trailing icon but without depending on that icon's label.
            composeRule.onNode(hasSetTextAction()).performImeAction()

            // Results stream in over REST polling (2s), so peers appear well before the search
            // completes. Wait for the first, then let a few more land for a fuller screen.
            waitForTag(SlskdTestTags.SEARCH_PEER_CARD, timeoutMillis = 120_000)
            gotResults = true
            settle(8_000)
        }

        capture("02-search-list") {
            // Guarded: if the search never navigated we're already on the list, and a blind back
            // press would leave the app.
            if (!textExists(SEARCH_FIELD_LABEL)) Espresso.pressBack()
            waitForTag(SlskdTestTags.SEARCH_ROW, timeoutMillis = 30_000)
            settle()
        }

        return gotResults
    }

    // --- Downloads / Uploads ------------------------------------------------------------------

    /**
     * Whatever transfer state the server already has. Both screens may legitimately be empty — we
     * don't queue anything to populate them — so the empty state is an acceptable capture and the
     * wait falls through to it rather than failing.
     */
    private fun captureTransferTabs() {
        capture("04-downloads") {
            openTab("Downloads")
            waitForContent(SlskdTestTags.TRANSFER_USER_CARD, "No downloads yet")
            settle()
        }
        capture("05-uploads") {
            openTab("Uploads")
            waitForContent(SlskdTestTags.TRANSFER_USER_CARD, "No uploads.")
            settle()
        }
    }

    // --- Rooms --------------------------------------------------------------------------------

    /**
     * The rooms list, then [room]'s chat. If we haven't joined it, join it through the same
     * find-rooms flow a user would: FAB → filter → Join.
     */
    private fun captureRooms() {
        capture("06-rooms") {
            openTab("Rooms")
            waitForContent(SlskdTestTags.ROOM_ROW, "You haven't joined any rooms")
            settle()
        }

        capture("07-room-chat") {
            if (!nodeExists(hasText(room))) joinRoom()

            composeRule.onAllNodesWithText(room).onFirst().performClick()
            // A quiet room shows "No messages yet" instead; that still makes a usable shot, so the
            // wait is best-effort. Room history is fetched wholesale every 2s.
            waitForContent(SlskdTestTags.ROOM_MESSAGE, "No messages yet", timeoutMillis = 45_000)
            settle(3_000)
        }

        // Back out of the room so the next tab switch starts from the list.
        runCatching { Espresso.pressBack() }
    }

    private fun joinRoom() {
        Log.i(TAG, "not a member of '$room' — joining")
        composeRule.onNodeWithContentDescription("Find rooms").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput(room)
        closeKeyboard()
        waitForTag(SlskdTestTags.AVAILABLE_ROOM_ROW, timeoutMillis = 45_000)
        // Rows are filtered by the query and sorted by user count, so the first is the busiest
        // room whose name contains [room] — for "slskd" that is the room itself.
        composeRule.onAllNodesWithText("Join").onFirst().performClick()
        // Closing the find-rooms sheet returns to the (now longer) joined list.
        waitUntil(30_000) { !nodeExists(hasText("Join")) || nodeExists(hasText(room)) }
        Espresso.pressBack()
        waitForTag(SlskdTestTags.ROOM_ROW, timeoutMillis = 30_000)
    }

    // --- Chat ---------------------------------------------------------------------------------

    /** The conversation list only — rendering a DM thread would mean sending one. */
    private fun captureChat() {
        capture("08-chat") {
            openTab("Chat")
            waitForContent(SlskdTestTags.CONVERSATION_ROW, "No conversations yet")
            settle()
        }
    }

    // --- Users / Browse -----------------------------------------------------------------------

    /**
     * Both of these need a real peer. Rather than hard-coding a username that may be offline on the
     * day, they're driven the way a user reaches them: from a search result's overflow menu. Peers
     * do go offline mid-search, so this tries the first few before giving up.
     */
    private fun capturePeerTabs() {
        var opened = false
        for (peerIndex in 0 until PEER_ATTEMPTS) {
            if (openPeerProfile(peerIndex)) {
                opened = true
                break
            }
            Log.i(TAG, "peer #$peerIndex didn't load a profile; trying the next")
        }
        if (!opened) {
            Log.w(TAG, "no peer profile loaded — skipping the Users and Browse shots")
            return
        }

        capture("09-user-profile") { settle() }

        capture("10-browse") {
            // The profile's Browse button opens the same peer in the Browse tab.
            composeRule.onNodeWithText("Browse").performClick()
            waitForTag(SlskdTestTags.BROWSE_TREE_ROW, timeoutMillis = 90_000)
            settle(2_000)
        }
    }

    /** Opens the search results, then peer [peerIndex]'s profile. True once its stats card renders. */
    private fun openPeerProfile(peerIndex: Int): Boolean = runCatching {
        openTab("Search")
        waitForTag(SlskdTestTags.SEARCH_ROW, timeoutMillis = 30_000)
        composeRule.onAllNodesWithTag(SlskdTestTags.SEARCH_ROW).onFirst().performClick()
        waitForTag(SlskdTestTags.SEARCH_PEER_CARD, timeoutMillis = 60_000)

        // "More actions for <username>" — the username is unknown here, hence the substring match.
        val menus = composeRule.onAllNodesWithContentDescription("More actions for", substring = true)
        if (menus.fetchSemanticsNodes().size <= peerIndex) return@runCatching false
        menus[peerIndex].performClick()
        composeRule.onNodeWithText("Info").performClick()

        // Users tab: Loading → Loaded (stats card) or Error ("They may be offline").
        waitUntil(45_000) {
            tagExists(SlskdTestTags.USER_STATS_CARD) || textExists("They may be offline")
        }
        tagExists(SlskdTestTags.USER_STATS_CARD)
    }.getOrElse {
        Log.w(TAG, "opening peer #$peerIndex failed", it)
        false
    }

    // --- Settings -----------------------------------------------------------------------------

    private fun captureSettings() {
        capture("11-settings") {
            // Settings is reachable from every top-level screen's gear, but on Users/Browse that
            // slot holds a close action while a peer is open — so start from Search.
            openTab("Search")
            waitForText(SEARCH_FIELD_LABEL)
            composeRule.onNodeWithContentDescription("Settings").performClick()
            waitForText("Message notifications", timeoutMillis = 15_000)
            settle()
        }
    }

    // --- Plumbing -----------------------------------------------------------------------------

    /** Switches top-level tab. The nav suite renders icons only; the label is the description. */
    private fun openTab(label: String) {
        composeRule.onNodeWithContentDescription(label).performClick()
    }

    /**
     * Runs [block] and captures [name]. A failure inside the block is logged and the screenshot
     * skipped — one unavailable screen must not cost us the other ten.
     */
    private fun capture(name: String, block: () -> Unit) {
        runCatching {
            block()
            screenshot(name)
        }.onFailure { Log.w(TAG, "skipping $name", it) }
    }

    private fun screenshot(name: String) {
        val bitmap: Bitmap = instrumentation.uiAutomation.takeScreenshot()
            ?: error("takeScreenshot() returned null for $name")
        val file = File(outputDir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        Log.i(TAG, "wrote ${file.absolutePath} (${file.length()} bytes)")
    }

    /**
     * Lets asynchronous work that Compose can't see finish — a REST poll landing, an avatar
     * decoding — before the shutter. Deliberately a sleep and not `waitForIdle()`: idleness only
     * covers composition, and these screens are driven by 2s polling loops.
     */
    private fun settle(millis: Long = 1_500) = Thread.sleep(millis)

    private fun closeKeyboard() {
        runCatching { Espresso.closeSoftKeyboard() }
        settle(500)
    }

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean) =
        composeRule.waitUntil(timeoutMillis, condition)

    private fun waitForTag(tag: String, timeoutMillis: Long = DEFAULT_TIMEOUT) =
        waitUntil(timeoutMillis) { tagExists(tag) }

    private fun waitForText(text: String, timeoutMillis: Long = DEFAULT_TIMEOUT) =
        waitUntil(timeoutMillis) { textExists(text) }

    /**
     * Waits for real content ([tag]) *or* the screen's empty state ([emptyText]). Downloads,
     * Uploads, Rooms and Chat can all be legitimately empty, and an empty screen is still a screen
     * worth a screenshot — so this returns either way rather than throwing.
     */
    private fun waitForContent(tag: String, emptyText: String, timeoutMillis: Long = DEFAULT_TIMEOUT) {
        runCatching {
            waitUntil(timeoutMillis) { tagExists(tag) || textExists(emptyText) }
        }.onFailure { Log.w(TAG, "neither '$tag' nor '$emptyText' appeared; capturing as-is") }
    }

    private fun tagExists(tag: String) =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun textExists(text: String) =
        composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    private fun nodeExists(matcher: SemanticsMatcher) =
        composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private companion object {
        const val TAG = "ScreenshotTest"
        const val DEFAULT_TIMEOUT = 30_000L

        /** Label of the Search tab's query field — the first thing unique to the signed-in shell. */
        const val SEARCH_FIELD_LABEL = "What are you looking for?"

        /** How many search peers to try before giving up on the Users/Browse shots. */
        const val PEER_ATTEMPTS = 3
    }
}
