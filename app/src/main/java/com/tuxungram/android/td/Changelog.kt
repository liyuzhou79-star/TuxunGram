package com.tuxungram.android.td

/** One version's user-facing release notes. */
data class ReleaseNotes(
    val version: String,
    val title: String,
    val items: List<String>
)

/**
 * In-app changelog (v0.9.6, P3): a local, offline copy of
 * what each version brought, written for users — what
 * changed and why it is useful, never internal detail.
 * Newest first. The settings 关于 page renders the whole
 * list; the "what's new" page shown once after an update
 * renders only the running version's entry. Entries start
 * at 0.5.0, the first packed big version.
 */
object Changelog {

    val releases: List<ReleaseNotes> = listOf(
        ReleaseNotes(
            "0.9.6", "回归与引导",
            listOf(
                "新手引导：第一次打开有几页介绍；更新到新版本后，进主界面会先告诉你这版有什么新东西",
                "更新日志页：设置 → 关于里可以翻看每一版改了什么",
                "设置备份：主题、代理等设置可以导出成一个文件，换手机或重装后能导回来（代理密码不进备份，导入后重填一次）",
                "切账号后，上一个账号的消息通知会一并清掉，不会点进错的对话",
                "从相册选图、选文件回来，不会再被应用锁误锁在半路",
                "编辑聊天文件夹更稳妥：成员没加载完时不能保存，不会误删文件夹里的对话",
                "共享媒体和照片说明里的粗体、链接，现在和聊天里一样可以点",
                "万一应用闪退，下次打开后可以在设置 → 关于查看本机的崩溃记录（只存这台手机，不上传）"
            )
        ),
        ReleaseNotes(
            "0.9.5", "管理与更新",
            listOf(
                "邀请链接管理：新建、复制、分享、撤销自己建的链接",
                "群设置：成员权限开关、慢速模式、内容保护（群主）",
                "群头像可以直接在资料页更换",
                "消息翻译：长按任意消息就能翻译成系统语言",
                "设置 → 关于里可以检查更新，有新版会给出说明和下载入口"
            )
        ),
        ReleaseNotes(
            "0.9.4", "创作与发现",
            listOf(
                "发起投票：普通投票和测验模式都能建",
                "发送位置和联系人卡片",
                "消息置顶：置顶栏可以逐条跳转，全部置顶一处看",
                "聊天背景可以换成自己相册里的照片",
                "贴纸面板新增热门贴纸包和按表情搜贴纸，整包一键添加"
            )
        ),
        ReleaseNotes(
            "0.9.3", "资料与隐私",
            listOf(
                "编辑我的资料：名字、简介、头像、用户名都能改",
                "隐私设置：谁能看到你的在线状态、头像、手机号等，逐项可调",
                "拉黑名单：资料页拉黑，设置里统一管理",
                "共享媒体页：在资料页按照片 / 视频 / 文件 / 链接翻历史媒体",
                "静默发送和定时发送：长按发送按钮选择，对方上线时发送也可以"
            )
        ),
        ReleaseNotes(
            "0.9.2", "富文本与表达",
            listOf(
                "消息格式完整显示：粗体、斜体、链接、剧透、代码块都和官方版一致",
                "自定义表情在消息里直接显示成图",
                "对话列表滑动操作：左滑置顶 / 静音，右滑归档 / 标未读",
                "群成员页：搜索成员，有权限时可把成员设为管理员",
                "语音消息在应用内播放：波形可拖动，1.5 倍 / 2 倍速可切"
            )
        ),
        ReleaseNotes(
            "0.9.1", "收尾补齐",
            listOf(
                "评论区和话题里也能发图片、文件，投票可以投了",
                "应用内视频播放器：点开视频直接看，不用跳外部应用",
                "通讯录页：按字母分组，谁在线一目了然",
                "收藏入口置顶：转发列表和对话列表最上面就是给自己留的收藏",
                "代理密码在本机改为加密保存"
            )
        ),
        ReleaseNotes(
            "0.9.0", "打磨版",
            listOf(
                "安装包从 41 MB 瘦身到 14.5 MB，下载和更新都更快",
                "冷启动更快，图片滚动更顺（加了图片缓存）",
                "修了一批隐藏问题：大照片预览更省内存、录音中途退出不再占着麦克风、文件夹报错改为人话提示等",
                "应用数据不再进入系统云备份，聊天记录更安全"
            )
        ),
        ReleaseNotes(
            "0.8.0", "内容面补齐",
            listOf(
                "频道评论区：点开评论就能看和发",
                "话题群组：话题列表、进话题聊天都齐了",
                "贴纸包管理：按链接添加、删除已装的贴纸包",
                "机器人消息里的按钮可以点了（链接、回调、网页）",
                "可以发动态了（发之前会确认可见范围）"
            )
        ),
        ReleaseNotes(
            "0.7.0", "消息体验",
            listOf(
                "手势：滑动消息回复、双击回应、多选消息批量转发 / 删除 / 复制",
                "图片查看器：双指缩放、左右滑动切换照片",
                "外观：聊天背景、气泡样式、字体大小都能调",
                "通知按对话分组，通知里可以直接回复或标已读",
                "草稿会跨设备同步；对方正在输入时顶栏会显示"
            )
        ),
        ReleaseNotes(
            "0.6.0", "加密与账号安全",
            listOf(
                "秘密聊天：端到端加密，可设阅后即焚时间",
                "应用锁：PIN 或指纹解锁，可设自动锁定时间",
                "已登录设备：查看都有谁登录着，退出其他设备",
                "代理列表：保存多个代理，一键切换，还能测速"
            )
        ),
        ReleaseNotes(
            "0.5.0", "大版本合集",
            listOf(
                "后台通知：应用切到后台，新消息也能提醒",
                "多账号：添加多个账号，一键切换",
                "主题换色：浅色 / 深色 / 跟随系统，四种主题色",
                "群管理工具、动态查看、聊天文件夹可以编辑了",
                "消息同步更可靠：老消息不会再时有时无",
                "全新图标；贴纸 / 表情 / GIF 面板就在输入框左边，点一下就能发"
            )
        )
    )

    /** Notes for one version, or null when it predates the
     *  local changelog. */
    fun notesFor(version: String): ReleaseNotes? =
        releases.firstOrNull { it.version == version }
}
