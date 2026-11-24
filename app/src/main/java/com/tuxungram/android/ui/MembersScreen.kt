package com.tuxungram.android.ui

import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.AdminStore
import com.tuxungram.android.td.DeepLink
import com.tuxungram.android.td.MemberEntry
import com.tuxungram.android.td.MemberListStore
import kotlinx.coroutines.delay

/**
 * The dedicated members page (v0.9.2, L4), reached from the
 * profile page's 成员 entry: administrators pinned in their
 * own section on top, the rest paged below, a search field
 * over searchChatMembers, and — only for viewers holding the
 * matching right — long-press management actions (mute /
 * unmute / ban / unban / promote / demote) through AdminStore.
 * No right, no entry: the dialog simply never offers the
 * action, same rule as the profile page.
 */
@Composable
fun MembersScreen(chatId: Long, onBack: () -> Unit) {
    BackHandler { onBack() }
    val state by MemberListStore.state.collectAsState()
    val caps by AdminStore.caps.collectAsState()
    val notice by AdminStore.notice.collectAsState()
    var query by remember { mutableStateOf("") }
    var actionTarget by remember { mutableStateOf<MemberEntry?>(null) }
    // Restrict-duration picker (v0.9.53, X4): muting asks
    // for how long first, like the official client.
    var restrictTarget by remember { mutableStateOf<MemberEntry?>(null) }
    // Administrator custom-title editor (v0.9.39, F2).
    var titleTarget by remember { mutableStateOf<MemberEntry?>(null) }
    var titleText by remember { mutableStateOf("") }
    var titleError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(chatId) {
        MemberListStore.load(chatId)
        AdminStore.loadCaps(chatId)
    }
    LaunchedEffect(query) {
        if (query.isNotBlank()) {
            delay(400)
            MemberListStore.search(chatId, query)
        } else if (state.searching) {
            MemberListStore.search(chatId, "")
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            val st = MemberListStore.state.value
            if (!st.exhausted && !st.searching &&
                lastVisible >= st.members.size - 8
            ) {
                MemberListStore.loadMore()
            }
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text(
                "成员",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            if (state.totalCount > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "${state.totalCount} 人",
                    color = Color.Gray, fontSize = 13.sp
                )
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索成员") },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
        if (notice != null) {
            Text(
                notice!!,
                fontSize = 13.sp, color = Color.Gray,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        val st = state
        if (st.chatId != chatId || (st.loading && st.members.isEmpty() &&
                st.admins.isEmpty())
        ) {
            CenterColumn {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在加载成员…", color = Color.Gray)
            }
            return@Column
        }
        if (!st.canList && st.admins.isEmpty()) {
            CenterColumn {
                Text(
                    "这个对话的成员名单不可见（管理员隐藏了成员，或这是一个成员不可读的频道）。",
                    color = Color.Gray
                )
            }
            return@Column
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .lazyListScrollbar(listState)
        ) {
            if (st.admins.isNotEmpty()) {
                item(key = "admins_header") {
                    Text(
                        "管理员",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray,
                        modifier = Modifier.padding(
                            horizontal = 16.dp, vertical = 8.dp
                        )
                    )
                }
                items(items = st.admins, key = { "admin_${it.userId}" }) { m ->
                    MemberRow(
                        member = m,
                        actionable = caps.canPromote &&
                            m.statusKind == "administrator",
                        onClick = {
                            DeepLink.pendingProfileUser.value = m.userId
                        },
                        onLongClick = { actionTarget = m }
                    )
                }
            }
            if (st.members.isNotEmpty()) {
                item(key = "members_header") {
                    Text(
                        if (st.searching) "搜索结果" else "成员",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray,
                        modifier = Modifier.padding(
                            horizontal = 16.dp, vertical = 8.dp
                        )
                    )
                }
                items(items = st.members, key = { it.userId }) { m ->
                    val actionable =
                        (caps.canRestrict && m.statusKind in
                            listOf("member", "restricted", "banned")) ||
                            (caps.canPromote && m.statusKind == "member")
                    MemberRow(
                        member = m,
                        actionable = actionable,
                        onClick = {
                            DeepLink.pendingProfileUser.value = m.userId
                        },
                        onLongClick = { actionTarget = m }
                    )
                }
            } else if (!st.searching) {
                item(key = "members_more") {
                    Text(
                        if (st.exhausted) "" else "正在加载更多成员…",
                        color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.padding(
                            horizontal = 16.dp, vertical = 8.dp
                        )
                    )
                }
            } else {
                item(key = "search_empty") {
                    Text(
                        "没有搜到这个成员",
                        color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.padding(
                            horizontal = 16.dp, vertical = 8.dp
                        )
                    )
                }
            }
        }
    }

    val target = actionTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = {
                Text(target.name.ifEmpty { "用户 ${target.userId}" })
            },
            text = {
                Column {
                    when (target.statusKind) {
                        "member" -> {
                            if (caps.canRestrict) {
                                TextButton(onClick = {
                                    restrictTarget = target
                                    actionTarget = null
                                }) { Text("禁言（不能发言）") }
                            }
                            if (caps.canPromote) {
                                TextButton(onClick = {
                                    AdminStore.promoteMember(
                                        chatId, target.userId
                                    )
                                    actionTarget = null
                                }) { Text("设为管理员") }
                            }
                            if (caps.canRestrict) {
                                TextButton(onClick = {
                                    AdminStore.banMember(
                                        chatId, target.userId
                                    )
                                    actionTarget = null
                                }) {
                                    Text(
                                        "封禁（移出并禁止再进）",
                                        color = Color(0xFFC62828)
                                    )
                                }
                            }
                        }
                        "restricted" -> {
                            TextButton(onClick = {
                                AdminStore.unrestrictMember(
                                    chatId, target.userId
                                )
                                actionTarget = null
                            }) { Text("解除禁言") }
                            TextButton(onClick = {
                                AdminStore.banMember(chatId, target.userId)
                                actionTarget = null
                            }) {
                                Text(
                                    "封禁（移出并禁止再进）",
                                    color = Color(0xFFC62828)
                                )
                            }
                        }
                        "banned" -> {
                            TextButton(onClick = {
                                AdminStore.unbanMember(chatId, target.userId)
                                actionTarget = null
                            }) { Text("解除封禁") }
                        }
                        "administrator" -> {
                            if (caps.canManageTags) {
                                TextButton(onClick = {
                                    titleText = target.tag
                                    titleError = null
                                    titleTarget = target
                                    actionTarget = null
                                }) { Text("设置头衔") }
                            }
                            if (caps.canPromote) {
                                TextButton(onClick = {
                                    AdminStore.demoteMember(
                                        chatId, target.userId
                                    )
                                    actionTarget = null
                                }) { Text("取消管理员") }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { actionTarget = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Restrict-duration picker (v0.9.53, X4): the tiers
    // are AdminStore.RESTRICT_DURATIONS; a timed mute
    // lifts by itself when restricted_until_date passes.
    val muting = restrictTarget
    if (muting != null) {
        AlertDialog(
            onDismissRequest = { restrictTarget = null },
            title = { Text("禁言多久") },
            text = {
                Column {
                    Text(
                        muting.name.ifEmpty { "用户 ${muting.userId}" },
                        fontSize = 13.sp, color = Color.Gray
                    )
                    for ((label, seconds) in
                        AdminStore.RESTRICT_DURATIONS) {
                        TextButton(onClick = {
                            AdminStore.restrictMember(
                                chatId, muting.userId, seconds
                            )
                            restrictTarget = null
                        }) { Text(label) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { restrictTarget = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Administrator custom-title editor (v0.9.39, F2):
    // the title is the member tag (setChatMemberTag) —
    // 0-16 characters, no emoji, per td_api.tl. The
    // client checks both rules up front in plain words;
    // the server stays the final judge and its refusal
    // lands in the shared admin notice. Saving an empty
    // field clears the title.
    val editing = titleTarget
    if (editing != null) {
        AlertDialog(
            onDismissRequest = { titleTarget = null },
            title = {
                Text(
                    "给 ${editing.name.ifEmpty { "这位管理员" }} 设置头衔"
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = {
                            titleText = it
                            titleError = null
                        },
                        singleLine = true,
                        label = { Text("头衔") },
                        isError = titleError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        titleError
                            ?: "最多 16 个字，不能带表情；" +
                                "留空保存就是清除头衔。",
                        fontSize = 12.sp,
                        color = if (titleError != null) {
                            Color(0xFFC62828)
                        } else {
                            Color.Gray
                        },
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cleaned = titleText.trim()
                    val codePoints = cleaned.codePointCount(
                        0, cleaned.length
                    )
                    val hasEmoji = cleaned.codePoints().anyMatch {
                        it >= 0x1F000 || it in 0x2600..0x27BF ||
                            Character.getType(it) ==
                            Character.SURROGATE.toInt()
                    }
                    when {
                        codePoints > 16 ->
                            titleError =
                                "头衔最多 16 个字，现在 $codePoints 个。"
                        hasEmoji ->
                            titleError = "头衔不能带表情，换成纯文字吧。"
                        else -> {
                            AdminStore.setMemberTitle(
                                chatId, editing.userId, cleaned
                            )
                            titleTarget = null
                        }
                    }
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { titleTarget = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemberRow(
    member: MemberEntry,
    actionable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (actionable) onLongClick() }
            )
            // Row metrics match the official member cell
            // (v0.9.49, T1): 46 dp avatar, 64 dp row.
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (member.photoPath != null) {
            AvatarImage(path = member.photoPath, sizeDp = 46)
        } else {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Color(0xFFB0BEC5), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    member.name.trim().firstOrNull()?.toString() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
        Spacer(Modifier.width(11.dp))
        Text(
            member.name.ifEmpty { "用户 ${member.userId}" },
            // Official member-cell name size (v0.9.49, T1 —
            // was 15). The right-hand status stays a small
            // privilege label by design (creator / admin only),
            // not the official presence line.
            fontSize = 17.sp,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
            maxLines = 1
        )
        if (member.statusText.isNotEmpty() && member.statusText != "成员") {
            if (member.statusKind == "creator") {
                Icon(
                    Icons.Filled.Star, contentDescription = "群主",
                    tint = Color(0xFFF9A825), modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
            } else if (member.statusKind == "administrator") {
                Icon(
                    ShieldIcon, contentDescription = "管理员",
                    tint = Color(0xFF5B7C99), modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(member.statusText, color = Color.Gray, fontSize = 12.sp)
        }
    }
}
