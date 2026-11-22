package com.tuxungram.android.td

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** One row of the traffic page (v0.9.14, U3): how much one
 *  file type moved over one network type, split by
 *  direction. Call entries fold into a 通话 row whose
 *  [durationSec] is set. */
data class TrafficRow(
    val label: String,
    val sentBytes: Long,
    val receivedBytes: Long,
    val durationSec: Double = 0.0
)

/**
 * Network statistics (v0.9.14, U3), over
 * getNetworkStatistics / resetNetworkStatistics (verified
 * in td_api.tl). The counters are TDLib's own local
 * bookkeeping: they accumulate from the first install (or
 * the last reset) on THIS device and account, and are not
 * synced anywhere. Rows are presented per file type with
 * the network type in the label; totals are summed locally
 * from the same entries, never invented.
 */
object NetworkStatsStore {
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _sinceDate = MutableStateFlow(0)
    val sinceDate: StateFlow<Int> = _sinceDate

    private val _rows = MutableStateFlow<List<TrafficRow>>(emptyList())
    val rows: StateFlow<List<TrafficRow>> = _rows

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice

    fun load() {
        _loading.value = true
        _error.value = null
        val query = TdClient.request("getNetworkStatistics").apply {
            put("only_current", false)
        }
        TdRepository.sendRaw(query) { response ->
            _loading.value = false
            if (TdClient.isError(response)) {
                _error.value =
                    "流量统计没拉下来，再试一次。（错误码 ${response.optInt("code")}）"
                return@sendRaw
            }
            _sinceDate.value = response.optInt("since_date", 0)
            val arr = response.optJSONArray("entries") ?: JSONArray()
            val rows = ArrayList<TrafficRow>()
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val sent = e.optLong("sent_bytes", 0)
                val received = e.optLong("received_bytes", 0)
                if (sent == 0L && received == 0L) continue
                val network = networkLabel(e.optString("network_type"))
                when (e.optString("@type")) {
                    "networkStatisticsEntryFile" -> rows.add(
                        TrafficRow(
                            label = "${fileTypeLabel(e.optString("file_type"))} · $network",
                            sentBytes = sent,
                            receivedBytes = received
                        )
                    )
                    "networkStatisticsEntryCall" -> rows.add(
                        TrafficRow(
                            label = "通话 · $network",
                            sentBytes = sent,
                            receivedBytes = received,
                            durationSec = e.optDouble("duration", 0.0)
                        )
                    )
                }
            }
            _rows.value = rows.sortedByDescending {
                it.sentBytes + it.receivedBytes
            }
        }
    }

    /** Zero the counters (resetNetworkStatistics), behind
     *  the page's own confirmation dialog. */
    fun reset() {
        TdRepository.sendRaw(
            TdClient.request("resetNetworkStatistics")
        ) { response ->
            if (TdClient.isError(response)) {
                _notice.value =
                    "重置没成，再试一次。（错误码 ${response.optInt("code")}）"
            } else {
                _notice.value = "已重置，从现在开始重新计数。"
                load()
            }
        }
    }

    fun totalSent(): Long = _rows.value.sumOf { it.sentBytes }
    fun totalReceived(): Long = _rows.value.sumOf { it.receivedBytes }

    private fun fileTypeLabel(type: String): String = when (type) {
        "fileTypePhoto" -> "照片"
        "fileTypeVideo" -> "视频"
        "fileTypeVideoNote" -> "视频消息"
        "fileTypeVoiceNote" -> "语音"
        "fileTypeDocument" -> "文件"
        "fileTypeSticker" -> "贴纸"
        "fileTypeAnimation" -> "动图"
        "fileTypeAudio" -> "音频"
        "fileTypeProfilePhoto" -> "头像"
        "fileTypeThumbnail" -> "缩略图"
        "fileTypePhotoStory", "fileTypeVideoStory" -> "动态"
        "fileTypeSecret" -> "秘密聊天"
        "fileTypeWallpaper" -> "壁纸"
        else -> "其他"
    }

    private fun networkLabel(type: String): String = when (type) {
        "networkTypeWiFi" -> "WiFi"
        "networkTypeMobile" -> "移动网络"
        "networkTypeMobileRoaming" -> "移动网络（漫游）"
        "networkTypeOther" -> "其他网络"
        else -> "未知网络"
    }
}
