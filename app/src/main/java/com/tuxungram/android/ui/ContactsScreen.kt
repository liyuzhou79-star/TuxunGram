package com.tuxungram.android.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.tuxungram.android.td.ContactEntry
import com.tuxungram.android.td.ContactsStore
import com.tuxungram.android.td.DeviceContactsSync

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

    // Device address-book sync (v0.9.13, T4): strictly
    // manual — an explanation dialog first, then the
    // READ_CONTACTS runtime permission, then the read +
    // importContacts run in DeviceContactsSync. A refused
    // permission stops the flow with an honest line.
    val context = LocalContext.current
    val syncStatus by DeviceContactsSync.status.collectAsState()
    val syncBusy by DeviceContactsSync.busy.collectAsState()
    var syncExplainOpen by remember { mutableStateOf(false) }
    val syncPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            DeviceContactsSync.sync(context)
        } else {
            DeviceContactsSync.notePermissionDenied()
        }
    }
    fun startSync() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            DeviceContactsSync.sync(context)
        } else {
            syncPermission.launch(Manifest.permission.READ_CONTACTS)
        }
    }

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

    val palette = OfficialTheme.current()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.windowBg)
    ) {
        // Official header + search (v0.9.20, AA3): the bar
        // row on the bar background, then the rounded gray
        // search field the official contacts page uses —
        // not an outlined text field.
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
            Text(
                "通讯录",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.actionBarTitle
            )
            if (contacts.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "${contacts.size} 人",
                    color = palette.textSecondary, fontSize = 13.sp
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(palette.divider)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(
                    palette.graySection,
                    RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search, contentDescription = null,
                tint = palette.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = palette.textPrimary, fontSize = 15.sp
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            "搜索联系人",
                            color = palette.textSecondary,
                            fontSize = 15.sp
                        )
                    }
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close, contentDescription = "清空",
                    tint = palette.textSecondary,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { query = "" }
                )
            }
        }
        // Sync entry (v0.9.13, T4): one tap, one explanation,
        // one permission ask — never automatic. It is this
        // page's top action row (v0.9.20, AA3): the official
        // contacts page heads the list with an accent action
        // row; the official's invite-friends row has no
        // backing feature in this app, so the real sync row
        // takes the slot instead of a decorative invite row.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !syncBusy) {
                    syncExplainOpen = true
                }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "同步手机通讯录",
                fontSize = 15.sp,
                color = palette.accent,
                modifier = Modifier.weight(1f)
            )
            if (syncBusy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (syncStatus != null) {
            Text(
                syncStatus!!,
                fontSize = 12.5.sp, color = palette.textSecondary,
                modifier = Modifier.padding(
                    start = 16.dp, end = 16.dp, bottom = 4.dp
                )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(palette.divider)
            )
        }
        if (shown.isEmpty()) {
            val loadFinished by ContactsStore.loadFinished.collectAsState()
            CenterColumn {
                if (!loadFinished) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在加载联系人…", color = Color.Gray)
                } else if (query.isNotEmpty()) {
                    EmptyState(
                        Icons.Filled.Search, "没有找到联系人",
                        "换个名字或用户名再搜搜看。"
                    )
                } else {
                    EmptyState(
                        PeopleIcon, "还没有联系人",
                        "在 Telegram 里加过的联系人会出现在这里。"
                    )
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
                            fontSize = 14.sp,
                            color = palette.textSecondary,
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

    if (syncExplainOpen) {
        // The pre-sync explanation (v0.9.13, T4): what leaves
        // the phone, said before anything is read or sent.
        AlertDialog(
            onDismissRequest = { syncExplainOpen = false },
            title = { Text("同步手机通讯录？") },
            text = {
                Text(
                    "同步会把手机通讯录里的姓名和手机号提交给 " +
                        "Telegram 服务器做匹配：对得上的人会被加进你的 " +
                        "Telegram 通讯录，对不上的不会有任何变化。" +
                        "只在这一次点按时同步，不会自动同步。"
                )
            },
            confirmButton = {
                Button(onClick = {
                    syncExplainOpen = false
                    startSync()
                }) { Text("开始同步") }
            },
            dismissButton = {
                TextButton(onClick = { syncExplainOpen = false }) {
                    Text("取消")
                }
            }
        )
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
    // Official contacts row (v0.9.20, AA3): 46 dp avatar
    // (the official placeholder gradient when there is no
    // photo — this page used to keep its own pastel set),
    // name, one secondary status line (accent while
    // online), nothing on the right, and a hairline under
    // the row inset to the text column.
    val palette = OfficialTheme.current()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (contact.photoPath != null) {
                AvatarImage(path = contact.photoPath, sizeDp = 46)
            } else {
                val pair = OfficialTheme.avatarColors(
                    contact.userId, OfficialTheme.dark
                )
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(pair.first, pair.second)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        contact.name.trim().firstOrNull()?.toString()
                            ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    contact.name,
                    color = palette.textPrimary,
                    fontSize = 16.sp,
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
                            palette.accentText
                        } else {
                            palette.textSecondary
                        },
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 74.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(palette.divider)
            )
        }
    }
}
