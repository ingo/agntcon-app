package org.jetbrains.kotlinconf.storage

actual interface Settings {
    actual fun putString(key: String, value: String)
    actual fun getString(key: String, defaultValue: String): String
    actual fun remove(key: String)
}

actual open class SettingsFactory {
    actual open fun create(name: String?): Settings {
        TODO("Not implemented because it should not be used in a backend")
    }
}