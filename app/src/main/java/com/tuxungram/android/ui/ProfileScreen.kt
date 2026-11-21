package com.tuxungram.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuxungram.android.td.ChatStore
import com.tuxungram.android.td.ProfileStore

/** Profile page (B5): user / group / channel info plus the
 *  member list where the chat allows reading it. Opened by
 *  tapping the chat screen's top bar. */
@Composable
fun ProfileScreen(chatId: Long, onBack: () -> Unit) {
    BackHandler { onBack() }
    val profile by ProfileStore.profile.collectAsState()
    val chatItem = ChatStore.itemOf(chatId)
    LaunchedEffect(chatId) { ProfileStore.load(chatId) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
            Text("资料", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
        val p = profile
        if (p == null || p.chatId != chatId) {
            CenterColumn {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text("正在加载资料…", color = Color.Gray)
            }
            return@Column
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "head") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (chatItem?.photoPath != null) {
                        AvatarImage(path = chatItem.photoPath, sizeDp = 84)
                    } else {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .background(Color(0xFF90CAF9), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                p.title.trim().firstOrNull()?.toString() ?: "?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 30.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        p.title,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(p.kindLabel, color = Color.Gray, fontSize = 13.sp)
                    for (line in p.infoLines) {
                        Text(line, color = Color.Gray, fontSize = 14.sp)
                    }
                }
            }
            if (p.description.isNotEmpty()) {
                item(key = "desc") {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            "简介",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(p.description, fontSize = 15.sp)
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
            if (p.members.isNotEmpty()) {
                item(key = "members_header") {
                    Text(
                        "成员",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                    )
                }
                items(items = p.members, key = { it.userId }) { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0xFFB0BEC5), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                member.name.trim().firstOrNull()?.toString() ?: "?",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            member.name.ifEmpty { "用户 ${member.userId}" },
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(member.statusText, color = Color.Gray, fontSize = 12.sp)
                    }
                }
                if (!p.membersExhausted) {
                    item(key = "members_more") {
                        TextButton(
                            onClick = { ProfileStore.loadMoreMembers() },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Text("加载更多成员")
                        }
                    }
                }
            } else if (p.canGetMembers) {
                item(key = "members_loading") {
                    Text(
                        "正在加载成员…",
                        color = Color.Gray, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }
        }
    }
}
