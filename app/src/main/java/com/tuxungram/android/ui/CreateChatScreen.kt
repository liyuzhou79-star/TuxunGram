package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.tuxungram.android.td.ContactsStore
import com.tuxungram.android.td.CreateStore

/** Create a basic group (pick members from contacts + a title)
 *  or a channel (title + description). On success the caller
 *  navigates straight into the new chat. */
@Composable
fun CreateChatScreen(
    onBack: () -> Unit,
    onCreated: (Long) -> Unit
) {
    BackHandler { onBack() }
    var mode by remember { mutableStateOf("group") }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var memberQuery by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val contacts by ContactsStore.contacts.collectAsState()
    val shownContacts = if (memberQuery.isBlank()) {
        contacts
    } else {
        ContactsStore.search(memberQuery)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("新建", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            TextButton(
                onClick = { mode = "group" },
                enabled = mode != "group"
            ) {
                Icon(
                    PeopleIcon, contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("群组")
                if (mode == "group") {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Check, contentDescription = "已选",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            TextButton(
                onClick = { mode = "channel" },
                enabled = mode != "channel"
            ) {
                Icon(
                    MegaphoneIcon, contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("频道")
                if (mode == "channel") {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Filled.Check, contentDescription = "已选",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(if (mode == "group") "群名" else "频道名") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        if (mode == "channel") {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("简介（可空）") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                maxLines = 3
            )
        }
        if (error != null) {
            Text(
                error!!,
                color = Color(0xFFC62828), fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                busy = true
                error = null
                if (mode == "group") {
                    CreateStore.createGroup(
                        title.trim(), selected.toList()
                    ) { chatId, err ->
                        busy = false
                        if (chatId != null) onCreated(chatId) else error = err
                    }
                } else {
                    CreateStore.createSupergroupOrChannel(
                        title.trim(), description.trim(), isChannel = true
                    ) { chatId, err ->
                        busy = false
                        if (chatId != null) onCreated(chatId) else error = err
                    }
                }
            },
            enabled = !busy && title.isNotBlank() &&
                (mode == "channel" || selected.isNotEmpty()),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(if (busy) "创建中…" else "创建")
        }
        if (mode == "group") {
            Spacer(Modifier.height(14.dp))
            Text(
                "选成员（已选 ${selected.size} 人）",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            OutlinedTextField(
                value = memberQuery,
                onValueChange = { memberQuery = it },
                placeholder = { Text("搜索联系人") },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            if (shownContacts.isEmpty()) {
                Text(
                    "联系人还没同步好，或者没有联系人。",
                    color = Color.Gray, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            val contactsListState = rememberLazyListState()
            LazyColumn(
                state = contactsListState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyListScrollbar(contactsListState)
            ) {
                items(items = shownContacts, key = { it.userId }) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (contact.userId in selected) {
                                    selected - contact.userId
                                } else {
                                    selected + contact.userId
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = contact.userId in selected,
                            onCheckedChange = { checked ->
                                selected = if (checked) {
                                    selected + contact.userId
                                } else {
                                    selected - contact.userId
                                }
                            }
                        )
                        Column {
                            Text(contact.name, fontSize = 15.sp)
                            if (contact.username.isNotEmpty()) {
                                Text(
                                    "@${contact.username}",
                                    color = Color.Gray, fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
