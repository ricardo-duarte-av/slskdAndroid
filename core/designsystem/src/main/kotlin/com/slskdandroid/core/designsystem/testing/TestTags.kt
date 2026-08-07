package com.slskdandroid.core.designsystem.testing

/**
 * Semantics tags applied to list rows and cards across the feature screens, so instrumentation can
 * wait for *content* rather than chrome.
 *
 * Every screen renders its title, tab bar and empty-state text the instant it composes, which makes
 * all of those useless as "the data arrived" signals — a screenshot taken on a title match captures
 * a spinner. A tagged row only exists once a repository has emitted, so waiting on one of these is
 * what makes the captures deterministic.
 *
 * They live in `core:designsystem` rather than in each feature because the only consumer is
 * `:app`'s `androidTest` source set, which can't see a feature module's `internal` declarations.
 * Keeping them in one place also stops the tag strings drifting from the test that waits on them.
 */
object SlskdTestTags {

    /** A row in the saved-searches list (Search tab). */
    const val SEARCH_ROW = "searchRow"

    /** A peer result card (depth 0) in a search's results. */
    const val SEARCH_PEER_CARD = "searchPeerCard"

    /** A peer card in Downloads or Uploads. Both screens share the tag; only one is ever on screen. */
    const val TRANSFER_USER_CARD = "transferUserCard"

    /** A joined-room row in the Rooms list. */
    const val ROOM_ROW = "roomRow"

    /** A row in the "Find rooms" (join a room) list. */
    const val AVAILABLE_ROOM_ROW = "availableRoomRow"

    /** A single message card inside an open room. */
    const val ROOM_MESSAGE = "roomMessage"

    /** A conversation row in the Chat list. */
    const val CONVERSATION_ROW = "conversationRow"

    /** A folder row in a peer's Browse tree. */
    const val BROWSE_TREE_ROW = "browseTreeRow"

    /** The stats card on a loaded user profile. */
    const val USER_STATS_CARD = "userStatsCard"
}
