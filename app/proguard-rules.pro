# TuxunGram ProGuard rules (release slimming, v0.9.0).

# The JNI bridge is resolved by name from native code
# (Java_com_tuxungram_android_td_TdNative_* symbols), so this
# class and its native methods must keep their names.
-keep class com.tuxungram.android.td.TdNative { *; }

# Everything else is reached through ordinary code or the
# manifest (whose entry points the default rules keep); the
# app uses no reflection-based class or resource lookups.
