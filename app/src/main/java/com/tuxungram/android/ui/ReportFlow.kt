package com.tuxungram.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tuxungram.android.td.BlockStore
import com.tuxungram.android.td.MessageStore
import com.tuxungram.android.td.ReportOptionUi
import com.tuxungram.android.td.ReportStep
import com.tuxungram.android.td.ReportStore

/** Where the report flow currently stands, UI-side. The
 *  server drives the steps (ReportStore flattens each
 *  reportChat answer); this composable only renders them. */
private sealed class ReportUiStep {
    data object Busy : ReportUiStep()
    data class Options(val options: List<ReportOptionUi>) : ReportUiStep()
    data class TextInput(
        val optionId: String,
        val optional: Boolean
    ) : ReportUiStep()
    data class Info(val text: String) : ReportUiStep()
}

/**
 * The report-a-chat dialog flow (v0.9.54, Y1/Y2), shared by
 * the chat screen's stranger strip and the profile page.
 * Mount it with a non-null target and it runs the initial
 * reportChat on its own; [blockUserId] > 0 turns a finished
 * report into the combined 举报并拉黑 (the block runs only
 * after the report itself succeeded — a cancelled or failed
 * report never blocks anyone silently). [onNotice] receives
 * the one plain-language outcome line; [onClose] unmounts.
 */
@Composable
fun ReportFlowDialogs(
    chatId: Long,
    blockUserId: Long = 0L,
    onClose: () -> Unit,
    onNotice: (String) -> Unit
) {
    var step by remember(chatId) {
        mutableStateOf<ReportUiStep>(ReportUiStep.Busy)
    }
    var pickedOption by remember(chatId) { mutableStateOf("") }
    var detailText by remember(chatId) { mutableStateOf("") }
    var messagesRetried by remember(chatId) { mutableStateOf(false) }

    fun finish() {
        if (blockUserId != 0L) {
            BlockStore.setUserBlocked(blockUserId, true) { error ->
                onNotice(
                    error ?: "已举报，并已拉黑对方：对方再发来的消息你不会收到。"
                )
                onClose()
            }
        } else {
            onNotice("举报已提交，平台会核查这个对话。")
            onClose()
        }
    }

    fun handle(result: ReportStep) {
        when (result) {
            is ReportStep.Done -> finish()
            is ReportStep.NeedOption ->
                step = ReportUiStep.Options(result.options)
            is ReportStep.NeedText -> {
                detailText = ""
                step = ReportUiStep.TextInput(
                    result.optionId, result.optional
                )
            }
            is ReportStep.NeedMessages -> {
                // The server wants the offending messages
                // named. In the open chat the loaded incoming
                // messages ARE the content the user is
                // reporting, so offer those ids once; anywhere
                // else (or with nothing loaded) say plainly
                // what is missing instead of guessing.
                val loaded = if (
                    chatId == MessageStore.currentChatId &&
                    !messagesRetried
                ) {
                    MessageStore.messages.value
                        .filter { !it.outgoing }
                        .takeLast(10)
                        .map { it.id }
                } else {
                    emptyList()
                }
                if (loaded.isNotEmpty()) {
                    messagesRetried = true
                    step = ReportUiStep.Busy
                    ReportStore.reportChat(
                        chatId,
                        optionId = pickedOption,
                        messageIds = loaded
                    ) { handle(it) }
                } else {
                    step = ReportUiStep.Info(
                        "平台要求先选定要举报的消息。这个对话里暂时没有可提交的消息，" +
                            "可以先拉黑对方（在对方资料页），举报等有消息时再进行。"
                    )
                }
            }
            is ReportStep.Failed ->
                step = ReportUiStep.Info(result.message)
        }
    }

    LaunchedEffect(chatId) {
        ReportStore.reportChat(chatId) { handle(it) }
    }

    when (val current = step) {
        is ReportUiStep.Busy -> {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text("举报") },
                text = {
                    Column {
                        CircularProgressIndicator()
                        Text("正在提交…")
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onClose) { Text("取消") }
                }
            )
        }
        is ReportUiStep.Options -> {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text("选择举报原因") },
                text = {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                    ) {
                        items(
                            items = current.options,
                            key = { it.id }
                        ) { option ->
                            TextButton(
                                onClick = {
                                    pickedOption = option.id
                                    step = ReportUiStep.Busy
                                    ReportStore.reportChat(
                                        chatId,
                                        optionId = option.id
                                    ) { handle(it) }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    option.text.ifEmpty { "其他" },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onClose) { Text("取消") }
                }
            )
        }
        is ReportUiStep.TextInput -> {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text("补充说明") },
                text = {
                    Column {
                        Text(
                            if (current.optional) {
                                "可以补充说明（可不填），帮助平台核查。"
                            } else {
                                "请补充说明，帮助平台核查。"
                            }
                        )
                        OutlinedTextField(
                            value = detailText,
                            onValueChange = { detailText = it },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 4
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            step = ReportUiStep.Busy
                            ReportStore.reportChat(
                                chatId,
                                optionId = current.optionId,
                                text = detailText.trim()
                            ) { handle(it) }
                        },
                        enabled = current.optional ||
                            detailText.isNotBlank()
                    ) { Text("提交") }
                },
                dismissButton = {
                    TextButton(onClick = onClose) { Text("取消") }
                }
            )
        }
        is ReportUiStep.Info -> {
            AlertDialog(
                onDismissRequest = onClose,
                title = { Text("举报") },
                text = { Text(current.text) },
                confirmButton = {
                    TextButton(onClick = onClose) { Text("知道了") }
                }
            )
        }
    }
}

/** Add-contact form for the stranger strip (v0.9.54, Y1):
 *  name and phone prefilled from the user object when the
 *  server hands them out (the phone often stays empty for
 *  strangers — the field is editable, and the entry only
 *  enables once a name and a number are present, the two
 *  things addContact cannot do without). */
@Composable
fun AddContactDialog(
    userId: Long,
    onClose: () -> Unit,
    onNotice: (String) -> Unit
) {
    var firstName by remember(userId) { mutableStateOf("") }
    var lastName by remember(userId) { mutableStateOf("") }
    var phone by remember(userId) { mutableStateOf("") }
    var loaded by remember(userId) { mutableStateOf(false) }
    LaunchedEffect(userId) {
        ReportStore.fetchUserBasics(userId) { first, last, number ->
            firstName = first
            lastName = last
            phone = number
            loaded = true
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("添加联系人") },
        text = {
            Column {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("名字") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("姓氏（可不填）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("手机号") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (loaded && phone.isBlank()) {
                    Text(
                        "对方没有公开手机号：知道的话可以在这里填上，" +
                            "填好后这个人就会出现在你的通讯录里。"
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    ReportStore.addContact(
                        userId,
                        phone = phone.trim(),
                        firstName = firstName.trim(),
                        lastName = lastName.trim()
                    ) { error ->
                        onNotice(error ?: "已添加到通讯录。")
                        if (error == null) onClose()
                    }
                },
                enabled = firstName.isNotBlank() && phone.isNotBlank()
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("取消") }
        }
    )
}
