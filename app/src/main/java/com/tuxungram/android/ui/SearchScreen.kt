package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.ContactsStore
import com.tuxungram.android.td.SearchStore
import kotlinx.coroutines.delay

/**
 * Full-screen search (v0.9.19, Z4): the official shape — the
 * top bar turns into the input (back arrow, field, clear
 * button), and results arrive grouped the official way:
 * chats, contacts, then global messages. Only the
 * presentation moved here; the data is the same as the old
 * in-list search (local title filter + ContactsStore.search
 * + SearchStore's paged searchMessagesText). Recent
 * searches are NOT shown: the app keeps no search-history
 * data source, and inventing one is out of this step's
 * presentation-only scope (recorded in the checkpoint).
 */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onOpenChatLocated: (Long, Long) -> Unit
) {
    BackHandler { onBack() }
    val palette = OfficialTheme.current()
    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val chats by ChatStore.chats.collectAsState()
    val hits by SearchStore.results.collectAsState()
    val searching by SearchStore.searching.collectAsState()
    val exhausted by SearchStore.exhausted.collectAsState()
    // Collected so the contacts section refreshes when the
    // address book finishes loading.
    val allContacts by ContactsStore.contacts.collectAsState()

    // Global message search, debounced — the same cadence
    // the in-list search used.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            SearchStore.search("")
        } else {
            delay(500)
            SearchStore.search(query)
        }
    }

    val q = query.trim()
    val chatHits = if (q.isEmpty()) {
        emptyList()
    } else {
        chats.filter { it.title.contains(q, ignoreCase = true) }
    }
    val contactHits = if (q.isEmpty()) {
        emptyList()
    } else {
        ContactsStore.search(q)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar = the input itself (official shape), on the
        // action-bar background with the hairline below.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.actionBarBg)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = palette.actionBarIcon
                )
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = palette.actionBarTitle, fontSize = 17.sp
                ),
                cursorBrush = SolidColor(palette.accent),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                "搜索",
                                color = palette.actionBarSubtitle,
                                fontSize = 17.sp
                            )
                        }
                        inner()
                    }
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { query = "" }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "清除",
                        tint = palette.actionBarIcon
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(palette.divider)
        )
        if (q.isEmpty()) {
            CenterColumn {
                EmptyState(
                    SearchIcon, "搜索聊天、联系人和消息",
                    "输入关键词后，结果会按聊天、联系人、消息分组显示。"
                )
            }
        } else {
            val searchListState = rememberLazyListState()
            LazyColumn(
                state = searchListState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyListScrollbar(searchListState)
            ) {
                if (chatHits.isNotEmpty()) {
                    item(key = "sec_chats") {
                        SearchSectionHeader("聊天")
                    }
                    items(items = chatHits, key = { it.id }) { chat ->
                        ChatRow(chat, onClick = { onOpenChat(chat.id) })
                    }
                }
                if (contactHits.isNotEmpty()) {
                    item(key = "sec_contacts") {
                        SearchSectionHeader("联系人")
                    }
                    items(
                        items = contactHits,
                        key = { "contact_${it.userId}" }
                    ) { contact ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    ContactsStore.openPrivateChat(
                                        contact.userId
                                    ) { chatId ->
                                        if (chatId != null) {
                                            onOpenChat(chatId)
                                        }
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (contact.photoPath != null) {
                                AvatarImage(
                                    path = contact.photoPath, sizeDp = 46
                                )
                            } else {
                                val pair = OfficialTheme.avatarColors(
                                    contact.userId, OfficialTheme.dark
                                )
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(pair.first, pair.second)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        contact.name.trim().firstOrNull()
                                            ?.toString() ?: "?",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    contact.name,
                                    color = palette.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp,
                                    maxLines = 1
                                )
                                val sub = when {
                                    contact.username.isNotEmpty() ->
                                        "@${contact.username}"
                                    contact.statusText.isNotEmpty() ->
                                        contact.statusText
                                    else -> ""
                                }
                                if (sub.isNotEmpty()) {
                                    Text(
                                        sub,
                                        color = palette.textSecondary,
                                        fontSize = 13.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
                item(key = "sec_messages") {
                    SearchSectionHeader("消息")
                }
                items(
                    items = hits,
                    key = { "hit_${it.chatId}_${it.messageId}" }
                ) { hit ->
                    SearchHitRow(hit) {
                        onOpenChatLocated(hit.chatId, hit.messageId)
                    }
                }
                if (searching) {
                    item(key = "hits_loading") {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = 16.dp, vertical = 10.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "搜索中…",
                                color = palette.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else if (!exhausted && hits.isNotEmpty()) {
                    item(key = "hits_more") {
                        TextButton(
                            onClick = { SearchStore.loadMore() },
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Text("加载更多结果")
                        }
                    }
                } else if (hits.isEmpty()) {
                    item(key = "hits_empty") {
                        Text(
                            "没有搜到消息",
                            color = palette.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(
                                horizontal = 16.dp, vertical = 10.dp
                            )
                        )
                    }
                }
                if (chatHits.isEmpty() && contactHits.isEmpty() &&
                    hits.isEmpty() && !searching
                ) {
                    item(key = "all_empty") {
                        EmptyState(
                            SearchIcon, "没有找到结果",
                            "换个关键词试试，或者检查一下拼写。"
                        )
                    }
                }
            }
        }
    }
}

/** Section header inside the search results (official
 *  grouping): a quiet band with a small semibold label. */
@Composable
private fun SearchSectionHeader(label: String) {
    val palette = OfficialTheme.current()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.graySection)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = palette.graySectionText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
