# TuxunGram

免费、开源的 Telegram 第三方 Android 客户端，基于 Telegram 官方的 [TDLib](https://github.com/tdlib/td) 库构建。

## 它是什么

- 一个干净、专注的 Telegram 客户端：登录你自己的 Telegram 账号，聊天数据始终在 Telegram 的服务器上，本 App 不经过、也不保存到任何第三方服务器。
- 完全免费，没有广告，没有内购，没有账号系统（除了你自己的 Telegram 账号）。
- 全程开源（GPLv3）：处理登录凭证和聊天内容的软件，代码就应该摆出来给大家看。

## 当前状态

开发中。第一版计划包含：

- 手机号登录（含两步验证）
- 对话列表与聊天历史
- 收发文字消息与图片
- 群组与频道浏览、发言

暂不计划：语音/视频通话、秘密聊天。

## 技术

- Kotlin + Jetpack Compose
- TDLib（官方 C++ 库）在 CI 中从源码编译为 arm64 的 `libtdjson.so`，App 通过其 JSON 接口与之通信
- 只做 Android（minSdk 29），arm64

## 构建

正式签名与 Telegram api 凭证都通过 GitHub Secrets 在 CI 注入，不写进仓库。

## 许可

GPLv3。TDLib 遵循 Boost Software License 1.0，OpenSSL 遵循 Apache License 2.0。
