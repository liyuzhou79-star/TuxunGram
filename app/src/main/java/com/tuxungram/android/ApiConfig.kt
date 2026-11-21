package com.tuxungram.android

/**
 * The single source of Telegram API credentials for the whole app.
 *
 * Values are baked in at build time (see app/build.gradle.kts):
 * CI Secrets win when present, otherwise a temporary publicly known
 * pair is used for development. Nothing else in the app may read
 * BuildConfig.API_ID / API_HASH directly, so replacing the
 * credentials later touches this one place only.
 */
object ApiConfig {
    val apiId: Int = BuildConfig.API_ID
    val apiHash: String = BuildConfig.API_HASH
}
