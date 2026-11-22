package com.tuxungram.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Create-a-checklist sheet (v0.9.24, Q7): a title plus 1–30
 * task rows (add/remove), and a switch for letting other
 * members tick tasks off. The payload is built in
 * MessageStore.sendChecklist (inputMessageChecklist,
 * verified in td_api.tl). Sending a checklist is
 * Premium-gated server-side; the refusal comes back as a
 * plain-language send error, so the sheet itself does not
 * pre-check the account.
 */
@Composable
fun ChecklistCreateDialog(
    onDismiss: () -> Unit,
    onSend: (
        title: String,
        tasks: List<String>,
        othersCanMark: Boolean
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    val tasks = remember { mutableStateListOf("", "") }
    var othersCanMark by remember { mutableStateOf(true) }

    val cleaned = tasks.map { it.trim() }.filter { it.isNotEmpty() }
    val valid = title.isNotBlank() && cleaned.isNotEmpty()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    "新建清单",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "发送清单是 Telegram Premium 功能，普通账号发送会被服务器拒绝，届时会给出提示。",
                    color = Color.Gray, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("清单标题") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                Spacer(Modifier.height(10.dp))
                tasks.forEachIndexed { index, value ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)
                    ) {
                        OutlinedTextField(
                            value = value,
                            onValueChange = { tasks[index] = it },
                            label = { Text("任务 ${index + 1}") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (tasks.size > 1) {
                            TextButton(onClick = {
                                tasks.removeAt(index)
                            }) {
                                Text("✕", color = Color(0xFFC62828))
                            }
                        }
                    }
                }
                if (tasks.size < 30) {
                    TextButton(onClick = { tasks.add("") }) {
                        Text("＋ 添加任务（${tasks.size}/30）")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        "别人也能勾选完成",
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = othersCanMark,
                        onCheckedChange = { othersCanMark = it }
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) { Text("取消") }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            onSend(
                                title.trim(), cleaned, othersCanMark
                            )
                        },
                        enabled = valid,
                        modifier = Modifier.weight(1f)
                    ) { Text("发送清单") }
                }
            }
        }
    }
}
