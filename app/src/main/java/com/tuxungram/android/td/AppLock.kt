package com.tuxungram.android.td

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * App lock (v0.6.0, E2): a local PIN (4-6 digits) and optional
 * fingerprint unlock, gating the whole UI on cold start and
 * after the app has spent the configured time in the
 * background. Purely local — nothing here talks to Telegram.
 *
 * The PIN is never stored in plain text: only a SHA-256 hash
 * of (random per-install salt + PIN) is persisted, plus the
 * PIN length (needed to auto-submit on the lock screen; the
 * length alone reveals nothing useful).
 */
object AppLock {
    private const val PREFS = "applock"
    private const val KEY_SALT = "pin_salt"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_LEN = "pin_len"
    private const val KEY_BIOMETRIC = "biometric"
    private const val KEY_AUTO_LOCK = "auto_lock_minutes"
    private const val KEY_FAIL_COUNT = "fail_count"
    private const val KEY_LOCKOUT_UNTIL = "lockout_until"

    /** Wrong-PIN throttle (audit fix A3): this many consecutive
     *  failures are free; past that, each failure locks entry
     *  for 30 s, doubling per further failure, capped at
     *  5 minutes. Without it a 4-digit PIN is only 10,000 fast
     *  taps away from anyone holding the phone. */
    private const val FREE_ATTEMPTS = 5

    /** Auto-lock choices in minutes; -1 = never re-lock while
     *  the process lives (a cold start still locks). */
    val AUTO_LOCK_OPTIONS = listOf(0, 1, 5, -1)

    fun autoLockLabel(minutes: Int): String = when (minutes) {
        0 -> "立即"
        1 -> "1 分钟"
        5 -> "5 分钟"
        -1 -> "从不"
        else -> "$minutes 分钟"
    }

    private val _pinSet = MutableStateFlow(false)
    val pinSet: StateFlow<Boolean> = _pinSet

    private val _biometricEnabled = MutableStateFlow(false)
    val biometricEnabled: StateFlow<Boolean> = _biometricEnabled

    private val _autoLockMinutes = MutableStateFlow(1)
    val autoLockMinutes: StateFlow<Int> = _autoLockMinutes

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    /** Epoch ms until which PIN entry is throttled after too
     *  many wrong attempts (0 = not throttled). */
    private val _lockoutUntilMs = MutableStateFlow(0L)
    val lockoutUntilMs: StateFlow<Long> = _lockoutUntilMs

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var backgroundedAt = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = prefs()
        _pinSet.value = prefs.getString(KEY_HASH, null) != null
        _biometricEnabled.value = prefs.getBoolean(KEY_BIOMETRIC, false)
        val saved = prefs.getInt(KEY_AUTO_LOCK, 1)
        _autoLockMinutes.value =
            if (saved in AUTO_LOCK_OPTIONS) saved else 1
        val until = prefs.getLong(KEY_LOCKOUT_UNTIL, 0L)
        _lockoutUntilMs.value =
            if (until > System.currentTimeMillis()) until else 0L
        // Cold start: a set PIN means the app starts locked.
        _locked.value = _pinSet.value
    }

    private fun prefs() =
        appContext!!.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun pinLength(): Int = prefs().getInt(KEY_LEN, 4)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        prefs().edit()
            .putString(KEY_SALT, saltHex)
            .putString(KEY_HASH, hash(saltHex, pin))
            .putInt(KEY_LEN, pin.length)
            .putInt(KEY_FAIL_COUNT, 0)
            .putLong(KEY_LOCKOUT_UNTIL, 0L)
            .apply()
        _lockoutUntilMs.value = 0L
        _pinSet.value = true
    }

    /** Milliseconds left in the wrong-PIN lockout (0 = entry
     *  allowed). While a lockout runs, verifyPin refuses even
     *  the correct PIN — the wait is the point. */
    fun lockoutRemainingMs(): Long {
        val until = _lockoutUntilMs.value
        return if (until > 0L) {
            maxOf(0L, until - System.currentTimeMillis())
        } else {
            0L
        }
    }

    fun verifyPin(pin: String): Boolean {
        val prefs = prefs()
        val salt = prefs.getString(KEY_SALT, null) ?: return false
        val expected = prefs.getString(KEY_HASH, null) ?: return false
        if (lockoutRemainingMs() > 0L) return false
        val ok = hash(salt, pin) == expected
        if (ok) {
            if (prefs.getInt(KEY_FAIL_COUNT, 0) != 0 ||
                prefs.getLong(KEY_LOCKOUT_UNTIL, 0L) != 0L
            ) {
                prefs.edit()
                    .putInt(KEY_FAIL_COUNT, 0)
                    .putLong(KEY_LOCKOUT_UNTIL, 0L)
                    .apply()
                _lockoutUntilMs.value = 0L
            }
        } else {
            val fails = prefs.getInt(KEY_FAIL_COUNT, 0) + 1
            val editor = prefs.edit().putInt(KEY_FAIL_COUNT, fails)
            if (fails >= FREE_ATTEMPTS) {
                val over = minOf(fails - FREE_ATTEMPTS, 4)
                val seconds = minOf(30L shl over, 300L)
                val until = System.currentTimeMillis() + seconds * 1000L
                editor.putLong(KEY_LOCKOUT_UNTIL, until)
                _lockoutUntilMs.value = until
            }
            editor.apply()
        }
        return ok
    }

    /** Remove the PIN entirely (caller verifies the current
     *  PIN first). Fingerprint unlock goes with it — it is
     *  only an alternative way past the same lock. */
    fun clearPin() {
        prefs().edit()
            .remove(KEY_SALT)
            .remove(KEY_HASH)
            .remove(KEY_LEN)
            .remove(KEY_FAIL_COUNT)
            .remove(KEY_LOCKOUT_UNTIL)
            .putBoolean(KEY_BIOMETRIC, false)
            .apply()
        _lockoutUntilMs.value = 0L
        _pinSet.value = false
        _biometricEnabled.value = false
        _locked.value = false
    }

    fun setBiometricEnabled(on: Boolean) {
        _biometricEnabled.value = on
        prefs().edit().putBoolean(KEY_BIOMETRIC, on).apply()
    }

    fun setAutoLockMinutes(minutes: Int) {
        if (minutes !in AUTO_LOCK_OPTIONS) return
        _autoLockMinutes.value = minutes
        prefs().edit().putInt(KEY_AUTO_LOCK, minutes).apply()
    }

    fun unlock() {
        _locked.value = false
    }

    @Volatile
    private var externalLaunchAt = 0L

    /** Stamp that the app is about to hand over to a system
     *  picker (photo / file / contact). Returning from a picker
     *  the app itself opened must not trip the auto-lock
     *  (regression fix R3, v0.9.6): with auto-lock set to
     *  立即 every picker return used to land on the lock
     *  screen, and with the 1-minute default a slow browse of
     *  the gallery locked the app mid-send. The stamp is
     *  one-shot and stays valid for 10 minutes — long enough
     *  for a slow pick, too short to be a standing bypass. */
    fun noteExternalLaunch() {
        externalLaunchAt = System.currentTimeMillis()
    }

    /** Activity went to background: remember when. */
    fun onBackground() {
        if (backgroundedAt == 0L) {
            backgroundedAt = System.currentTimeMillis()
        }
    }

    /** Activity is foreground again: re-lock if the configured
     *  timeout has elapsed since backgrounding. */
    fun onForeground() {
        val since = backgroundedAt
        backgroundedAt = 0L
        val launchedAt = externalLaunchAt
        externalLaunchAt = 0L
        if (launchedAt != 0L &&
            System.currentTimeMillis() - launchedAt < 10 * 60_000L
        ) {
            return
        }
        if (!_pinSet.value || since == 0L) return
        val minutes = _autoLockMinutes.value
        if (minutes < 0) return
        val elapsed = System.currentTimeMillis() - since
        if (elapsed >= minutes * 60_000L) {
            _locked.value = true
        }
    }

    /** Whether this device can actually do fingerprint unlock
     *  (hardware present + at least one fingerprint enrolled).
     *  Framework BiometricManager: since v0.9.0 (H3) the app
     *  uses the platform biometric APIs (minSdk 29 covers
     *  them) instead of the androidx.biometric library, whose
     *  dependency tree cost about 2 MB of package. */
    fun biometricAvailable(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(
                android.hardware.biometrics.BiometricManager::class.java
            ) ?: return false
            val result = if (android.os.Build.VERSION.SDK_INT >= 30) {
                manager.canAuthenticate(
                    android.hardware.biometrics.BiometricManager
                        .Authenticators.BIOMETRIC_STRONG
                )
            } else {
                @Suppress("DEPRECATION")
                manager.canAuthenticate()
            }
            result ==
                android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
        } catch (t: Throwable) {
            false
        }
    }

    private fun hash(saltHex: String, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$saltHex:$pin".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
