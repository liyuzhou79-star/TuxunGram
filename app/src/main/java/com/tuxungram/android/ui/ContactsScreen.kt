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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.ContactEntry
import com.tuxungram.android.td.ContactsStore

/**
 * Contacts page (v0.9.1, J4): the full Telegram address book
 * from ContactsStore (getContacts + getUser per contact, the
 * same data the global search already used), grouped by first
 * letter with a search box, presence line per contact, and a
 * tap opening the private chat. On a fresh login the book is
 * still arriving while this page is open — the list fills in
 * live from the store's flow, which is the user's full-sync
 * requirement applied to contacts.
 */
@Composable
fun ContactsScreen(
    onBack: () -> Unit,
    onOpenChat: (Long) -> Unit
) {
    BackHandler { onBack() }
    val contacts by ContactsStore.contacts.collectAsState()
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { ContactsStore.loadContacts() }

    val shown = if (query.isBlank()) {
        contacts
    } else {
        ContactsStore.search(query)
    }
    // Letter groups: A–Z buckets first (by the name's first
    // letter), everything else under its first character.
    val groups = shown.groupBy { groupKeyOf(it.name) }
    val orderedKeys = groups.keys.sortedWith { a, b ->
        val aLetter = a.length == 1 && a[0] in 'A'..'Z'
        val bLetter = b.length == 1 && b[0] in 'A'..'Z'
        when {
            aLetter && bLetter -> a.compareTo(b)
            aLetter -> -1
            bLetter -> 1
            else -> a.compareTo(b)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回"
                )
            }
            Text(
                "通讯录",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (contacts.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "${contacts.size} 人",
                    color = Color.Gray, fontSize = 13.sp
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索联系人") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        if (shown.isEmpty()) {
            CenterColumn {
                if (contacts.isEmpty()) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载联系人…", color = Color.Gray)
                } else {
                    Text("没有找到联系人", color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyListScrollbar(listState)
            ) {
                for (key in orderedKeys) {
                    item(key = "group_$key") {
                        Text(
                            key,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                horizontal = 16.dp, vertical = 6.dp
                            )
                        )
                    }
                    items(
                        items = groups[key].orEmpty(),
                        key = { "contact_${it.userId}" }
                    ) { contact ->
                        ContactRow(contact) {
                            ContactsStore.openPrivateChat(contact.userId) {
                                if (it != null) onOpenChat(it)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Group bucket for a display name: its first letter,
 *  uppercased, when it has one; otherwise the first character
 *  itself (Chinese names group under their first character,
 *  the way phone books group by surname). */
private fun groupKeyOf(name: String): String {
    val first = name.trim().firstOrNull() ?: return "#"
    return if (first.isLetter()) {
        first.uppercaseChar().toString()
    } else {
        first.toString()
    }
}

@Composable
private fun ContactRow(contact: ContactEntry, onClick: () -> Unit) {
    val hue = (contact.userId % 8 + 8) % 8
    val colors = listOf(
        Color(0xFFEF9A9A), Color(0xFFF48FB1), Color(0xFFCE93D8),
        Color(0xFF90CAF9), Color(0xFF80CBC4), Color(0xFFA5D6A7),
        Color(0xFFFFCC80), Color(0xFFBCAAA4)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colors[hue.toInt()], CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                contact.name.trim().firstOrNull()?.toString() ?: "?",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                contact.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1
            )
            val sub = buildList {
                if (contact.statusText.isNotEmpty()) {
                    add(contact.statusText)
                }
                if (contact.username.isNotEmpty()) {
                    add("@${contact.username}")
                }
            }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    sub,
                    color = if (contact.isOnline) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Gray
                    },
                    fontSize = 13.sp,
                    maxLines = 1
                )
            }
        }
    }
}
