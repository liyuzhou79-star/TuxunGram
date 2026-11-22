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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
 * Create-a-poll sheet (v0.9.4, N1): question, 2–10 options
 * (add/remove rows), anonymous and multiple-answer switches,
 * and a quiz mode with one marked correct option plus an
 * explanation. The payload is built in MessageStore.sendPoll
 * (inputMessagePoll, verified in td_api.tl); quiz mode and
 * multiple answers are mutually exclusive server-side, so
 * turning quiz on forces multiple off.
 */
@Composable
fun PollCreateDialog(
    onDismiss: () -> Unit,
    onSend: (
        question: String,
        options: List<String>,
        anonymous: Boolean,
        multiple: Boolean,
        quizCorrectIndex: Int,
        quizExplanation: String
    ) -> Unit
) {
    var question by remember { mutableStateOf("") }
    val options = remember { mutableStateListOf("", "") }
    var anonymous by remember { mutableStateOf(true) }
    var multiple by remember { mutableStateOf(false) }
    var quiz by remember { mutableStateOf(false) }
    var correctIndex by remember { mutableIntStateOf(0) }
    var explanation by remember { mutableStateOf("") }

    // Cleaned options keep their original positions so the
    // quiz's correct pick maps onto the sent list correctly.
    val cleaned = options.mapIndexedNotNull { index, text ->
        val trimmed = text.trim()
        if (trimmed.isEmpty()) null else index to trimmed
    }
    val cleanedCorrect = cleaned.indexOfFirst { it.first == correctIndex }
    val valid = question.isNotBlank() && cleaned.size >= 2 &&
        (!quiz || cleanedCorrect >= 0)

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
                    "发起投票",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("问题") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Spacer(Modifier.height(10.dp))
                options.forEachIndexed { index, value ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 3.dp)
                    ) {
                        if (quiz) {
                            RadioButton(
                                selected = correctIndex == index,
                                onClick = { correctIndex = index }
                            )
                        }
                        OutlinedTextField(
                            value = value,
                            onValueChange = { options[index] = it },
                            label = { Text("选项 ${index + 1}") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        if (options.size > 2) {
                            TextButton(onClick = {
                                options.removeAt(index)
                                if (correctIndex >= options.size) {
                                    correctIndex = options.size - 1
                                }
                            }) {
                                Text("✕", color = Color(0xFFC62828))
                            }
                        }
                    }
                }
                if (options.size < 10) {
                    TextButton(onClick = { options.add("") }) {
                        Text("＋ 添加选项（${options.size}/10）")
                    }
                }
                if (quiz) {
                    Text(
                        "在测验模式下点选左侧圆圈，标出正确答案。",
                        color = Color.Gray, fontSize = 12.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = explanation,
                        onValueChange = { explanation = it },
                        label = { Text("答案解析（可不填）") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
                Spacer(Modifier.height(8.dp))
                SwitchRow("匿名投票", anonymous) { anonymous = it }
                SwitchRow("允许多选", multiple && !quiz) {
                    if (!quiz) multiple = it
                }
                if (quiz) {
                    Text(
                        "测验模式只能单选。",
                        color = Color.Gray, fontSize = 12.sp
                    )
                }
                SwitchRow("测验模式", quiz) {
                    quiz = it
                    if (it) multiple = false
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
                                question.trim(),
                                cleaned.map { it.second },
                                anonymous,
                                if (quiz) false else multiple,
                                if (quiz) cleanedCorrect else -1,
                                explanation.trim()
                            )
                        },
                        enabled = valid,
                        modifier = Modifier.weight(1f)
                    ) { Text("发起投票") }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
