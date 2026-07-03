package com.slskdandroid.core.data

import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.slskdandroid.core.common.IoDispatcher
import com.slskdandroid.core.network.SlskdApi
import com.slskdandroid.core.network.model.NetworkPrivateMessage
import com.slskdandroid.core.network.model.NetworkRoomMessage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls slskd for new private messages and room mentions and posts Android message-style
 * notifications for them. Driven by the notification foreground service, which calls [scanOnce] on
 * the user's chosen interval.
 *
 * De-duplication is watermark-based and kept in memory (the service is meant to be permanent):
 * the **first** scan after process start silently records the newest message per DM / room without
 * notifying (so enabling the feature doesn't dump the entire backlog into the shade), and later
 * scans only notify messages newer than that watermark. All timestamps compared are slskd's own
 * server timestamps, so device/server clock skew can't misfire the baseline.
 */
@Singleton
class MessageNotifier @Inject constructor(
    private val api: SlskdApi,
    private val avatarRepository: AvatarRepository,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    // Newest notified/seen server-timestamp per conversation peer and per room.
    private val dmWatermark = ConcurrentHashMap<String, Long>()
    private val roomWatermark = ConcurrentHashMap<String, Long>()

    @Volatile private var baselined = false

    // Floor applied to DMs/rooms first seen *after* the initial baseline (e.g. a brand-new
    // conversation), so their pre-existing history isn't announced retroactively.
    @Volatile private var baselineFloorMs = 0L

    @Volatile private var cachedUsername: String? = null

    /** One poll cycle: fetch DMs and room chatter, then notify anything new. Never throws. */
    suspend fun scanOnce() = withContext(ioDispatcher) {
        ensureChannel()
        val username = ensureUsername()
        Log.d(TAG, "scan start — username=$username, baselined=$baselined")

        val dms: List<Pair<String, List<NetworkPrivateMessage>>> =
            runCatching { api.getConversations(includeInactive = true, unAcknowledgedOnly = false) }
                .onFailure { Log.w(TAG, "getConversations failed", it) }
                .getOrDefault(emptyList())
                .mapNotNull { conv ->
                    val messages = runCatching { api.getMessages(conv.username) }.getOrNull() ?: return@mapNotNull null
                    conv.username to messages
                }

        val rooms: List<Pair<String, List<NetworkRoomMessage>>> =
            runCatching { api.getJoinedRooms() }
                .onFailure { Log.w(TAG, "getJoinedRooms failed", it) }
                .getOrDefault(emptyList())
                .mapNotNull { room ->
                    val messages = runCatching { api.getRoomMessages(room) }.getOrNull() ?: return@mapNotNull null
                    room to messages
                }

        Log.d(TAG, "fetched ${dms.size} conversation(s), ${rooms.size} joined room(s)")

        if (!baselined) {
            establishBaseline(dms, rooms, username)
            Log.d(TAG, "baseline established at floor=$baselineFloorMs — no notifications this cycle")
            return@withContext
        }

        dms.forEach { (peer, messages) -> notifyNewDirectMessages(peer, messages) }
        if (username != null) {
            rooms.forEach { (room, messages) -> notifyNewRoomMentions(room, messages, username) }
        } else {
            Log.d(TAG, "username unknown — skipping room-mention scan")
        }
    }

    private fun establishBaseline(
        dms: List<Pair<String, List<NetworkPrivateMessage>>>,
        rooms: List<Pair<String, List<NetworkRoomMessage>>>,
        username: String?,
    ) {
        var maxSeen = 0L
        dms.forEach { (peer, messages) ->
            val mx = messages.filter { it.isIncoming }.mapNotNull { it.epochMillis }.maxOrNull()
            if (mx != null) {
                dmWatermark[peer] = mx
                maxSeen = maxOf(maxSeen, mx)
            }
        }
        rooms.forEach { (room, messages) ->
            // Baseline *every* joined room to its newest message so its backlog is never replayed
            // (marks the room "known" so a room joined later is recognised as new). Mention
            // timestamps also feed the global floor used to gate brand-new DM conversations.
            if (username != null) {
                messages.filter { it.mentions(username) }.mapNotNull { it.epochMillis }.maxOrNull()
                    ?.let { maxSeen = maxOf(maxSeen, it) }
            }
            roomWatermark[room] = messages.mapNotNull { it.epochMillis }.maxOrNull() ?: 0L
        }
        baselineFloorMs = maxSeen
        baselined = true
    }

    private suspend fun notifyNewDirectMessages(peer: String, messages: List<NetworkPrivateMessage>) {
        val incoming = messages.filter { it.isIncoming && it.epochMillis != null }
        if (incoming.isEmpty()) return
        val floor = dmWatermark[peer] ?: baselineFloorMs
        val fresh = incoming.filter { it.epochMillis!! > floor }.sortedBy { it.epochMillis }
        dmWatermark[peer] = maxOf(floor, incoming.maxOf { it.epochMillis!! })
        Log.d(TAG, "DM $peer: ${incoming.size} incoming, ${fresh.size} new past floor=$floor")
        if (fresh.isEmpty()) return
        Log.i(TAG, "posting DM notification from $peer (${fresh.size} message(s))")

        val avatar = runCatching { avatarRepository.getAvatar(peer) }.getOrNull()
        val self = Person.Builder().setName(SELF_NAME).build()
        val sender = Person.Builder().setName(peer).apply {
            avatar?.toBitmap()?.let { setIcon(IconCompat.createWithBitmap(it)) }
        }.build()
        val style = NotificationCompat.MessagingStyle(self)
        fresh.forEach { style.addMessage(it.message, it.epochMillis!!, sender) }

        post(tag = peer, id = DM_NOTIFICATION_ID) {
            setStyle(style)
            avatar?.toBitmap()?.let { setLargeIcon(it) }
        }
    }

    private fun notifyNewRoomMentions(room: String, messages: List<NetworkRoomMessage>, username: String) {
        // A room first seen after the initial baseline — i.e. joined from another slskd client
        // mid-session. Silently baseline it to its newest message so we don't replay its backlog;
        // only mentions arriving afterwards notify.
        if (!roomWatermark.containsKey(room)) {
            val newest = messages.mapNotNull { it.epochMillis }.maxOrNull() ?: baselineFloorMs
            roomWatermark[room] = newest
            Log.d(TAG, "room $room: first seen (joined elsewhere?) — baselined silently at $newest")
            return
        }
        val floor = roomWatermark.getValue(room)
        val mentions = messages.filter { !it.isSelf && it.epochMillis != null && it.mentions(username) }
        Log.d(TAG, "room $room: ${messages.size} message(s), ${mentions.size} mention(s) of '$username'")
        if (mentions.isEmpty()) return
        val fresh = mentions.filter { it.epochMillis!! > floor }.sortedBy { it.epochMillis }
        roomWatermark[room] = maxOf(floor, mentions.maxOf { it.epochMillis!! })
        Log.d(TAG, "room $room: ${fresh.size} new mention(s) past floor=$floor")
        if (fresh.isEmpty()) return
        Log.i(TAG, "posting room notification for $room (${fresh.size} mention(s))")

        val self = Person.Builder().setName(SELF_NAME).build()
        val style = NotificationCompat.MessagingStyle(self).setConversationTitle(room).setGroupConversation(true)
        fresh.forEach { msg ->
            val sender = Person.Builder().setName(msg.username).build()
            style.addMessage(msg.message, msg.epochMillis!!, sender)
        }

        post(tag = room, id = ROOM_NOTIFICATION_ID) { setStyle(style) }
    }

    private fun post(tag: String, id: Int, configure: NotificationCompat.Builder.() -> Unit) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(launchIntent())
            .apply(configure)
            .build()
        // No-ops silently if POST_NOTIFICATIONS isn't granted (requested from the UI).
        runCatching { notificationManager.notify(tag, id, notification) }
    }

    private fun launchIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Messages")
            .setDescription("New private messages and room mentions")
            .build()
        notificationManager.createNotificationChannel(channel)
    }

    private suspend fun ensureUsername(): String? {
        cachedUsername?.let { return it }
        return runCatching { api.getApplicationInfo().user?.username }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.also { cachedUsername = it }
    }
}

private val NetworkPrivateMessage.isIncoming: Boolean
    get() = direction?.equals("In", ignoreCase = true) == true

private val NetworkPrivateMessage.epochMillis: Long?
    get() = timestamp?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

private val NetworkRoomMessage.isSelf: Boolean get() = self == true

private val NetworkRoomMessage.epochMillis: Long?
    get() = timestamp?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

/** Case-insensitive substring match — Soulseek has no formal mention syntax. */
private fun NetworkRoomMessage.mentions(username: String): Boolean =
    message.contains(username, ignoreCase = true)

private fun ByteArray.toBitmap(): Bitmap? =
    runCatching { BitmapFactory.decodeByteArray(this, 0, size) }.getOrNull()

private const val TAG = "MessageNotifier"
private const val CHANNEL_ID = "messages"
private const val SELF_NAME = "You"
private const val DM_NOTIFICATION_ID = 1001
private const val ROOM_NOTIFICATION_ID = 1002
