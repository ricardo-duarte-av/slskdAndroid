package com.slskdandroid.feature.users.impl

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.slskdandroid.core.model.UserPresence
import com.slskdandroid.core.model.UserProfile

@Composable
internal fun UsersRoute(
    onBrowseUser: (String) -> Unit,
    onChatUser: (String) -> Unit,
    viewModel: UsersViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    UsersScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onBrowseUser = onBrowseUser,
        onChatUser = onChatUser,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsersScreen(
    uiState: UsersUiState,
    onAction: (UsersAction) -> Unit,
    onBrowseUser: (String) -> Unit,
    onChatUser: (String) -> Unit,
) {
    // While a user is open/loading, back returns to the prompt rather than leaving the tab.
    BackHandler(enabled = uiState !is UsersUiState.Idle) { onAction(UsersAction.Close) }

    Scaffold(
        topBar = { UsersTopBar(uiState, onAction) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (uiState) {
                is UsersUiState.Idle -> IdlePrompt(uiState.query, onAction)

                is UsersUiState.Loading -> CenteredContent {
                    CircularProgressIndicator()
                    Text("Loading ${uiState.username}…", style = MaterialTheme.typography.bodyLarge)
                }

                is UsersUiState.Error -> CenteredContent {
                    Text(
                        uiState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = { onAction(UsersAction.Retry) }) { Text("Retry") }
                }

                is UsersUiState.Loaded -> UserProfileContent(
                    profile = uiState.profile,
                    onBrowse = { onBrowseUser(uiState.profile.username) },
                    onChat = { onChatUser(uiState.profile.username) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsersTopBar(uiState: UsersUiState, onAction: (UsersAction) -> Unit) {
    val title = when (uiState) {
        is UsersUiState.Idle -> "Users"
        is UsersUiState.Loading -> uiState.username
        is UsersUiState.Error -> uiState.username
        is UsersUiState.Loaded -> uiState.profile.username
    }
    TopAppBar(
        title = { Text(title) },
        actions = {
            if (uiState !is UsersUiState.Idle) {
                IconButton(onClick = { onAction(UsersAction.Close) }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close user")
                }
            }
        },
    )
}

@Composable
private fun IdlePrompt(query: String, onAction: (UsersAction) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { onAction(UsersAction.QueryChanged(it)) },
            label = { Text("Username") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { onAction(UsersAction.Submit) }) {
                    Icon(Icons.Filled.Person, contentDescription = "Look up user")
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onAction(UsersAction.Submit) }),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        CenteredMessage("Enter a username to see their profile.")
    }
}

@Composable
private fun UserProfileContent(
    profile: UserProfile,
    onBrowse: () -> Unit,
    onChat: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileHeader(profile)

        StatsCard(profile)

        // Arbitrary free text the peer wrote about themselves.
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "About",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    profile.description ?: "No user info.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (profile.description == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onBrowse, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Browse")
            }
            OutlinedButton(onClick = onChat, modifier = Modifier.weight(1f)) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Chat")
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: UserProfile) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ProfilePicture(profile.pictureBase64)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                profile.username,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            PresenceLabel(profile.presence, profile.isPrivileged)
        }
    }
}

@Composable
private fun ProfilePicture(base64: String?) {
    val bitmap = remember(base64) { base64?.let(::decodeBase64Image) }
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(shape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Profile picture",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PresenceLabel(presence: UserPresence, isPrivileged: Boolean) {
    val (text, color) = when (presence) {
        UserPresence.Online -> "Online" to MaterialTheme.colorScheme.primary
        UserPresence.Away -> "Away" to MaterialTheme.colorScheme.tertiary
        UserPresence.Offline -> "Offline" to MaterialTheme.colorScheme.onSurfaceVariant
        UserPresence.Unknown -> "Status unknown" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(8.dp).clip(CircleShape),
            content = { Surface(color = color, modifier = Modifier.fillMaxSize()) {} },
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
        if (isPrivileged) {
            Text(
                "  ·  Privileged",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun StatsCard(profile: UserProfile) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            StatRow("Free upload slot", if (profile.hasFreeUploadSlot) "Yes" else "No")
            StatDivider()
            StatRow("Total upload slots", profile.uploadSlots.toString())
            StatDivider()
            StatRow("Queue length", profile.queueLength.toString())
            StatDivider()
            StatRow("IP address", profile.ipAddress ?: "Unknown")
            StatDivider()
            StatRow("Port", profile.port?.toString() ?: "Unknown")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StatDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun CenteredMessage(
    message: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

@Composable
private fun CenteredContent(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

/** Decodes a base64 profile picture; returns null if the payload isn't a valid image. */
private fun decodeBase64Image(base64: String): android.graphics.Bitmap? = runCatching {
    val bytes = Base64.decode(base64, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()
