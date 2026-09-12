package com.dotancohen.voiceandroid.testing

import android.content.SharedPreferences

/**
 * A [SharedPreferences] that lives in a map, so the settings classes can be
 * tested without a phone.
 *
 * It keeps the one behaviour of the real thing that the settings rely on:
 * a value written is the value read back, and a key never written returns
 * the default that was asked for. Listeners are not supported, because
 * nothing in this application uses them.
 */
class FakePreferences(initial: Map<String, Any> = emptyMap()) : SharedPreferences {

    private val values: MutableMap<String, Any> = initial.toMutableMap()

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? =
        values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key] as? MutableSet<String> ?: defValues

    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean =
        values[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearFirst = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { pending[key] = null }
        override fun clear() = apply { clearFirst = true }

        override fun commit(): Boolean {
            write()
            return true
        }

        override fun apply() = write()

        private fun write() {
            if (clearFirst) values.clear()
            for ((key, value) in pending) {
                if (value == null) values.remove(key) else values[key] = value
            }
            pending.clear()
            clearFirst = false
        }
    }
}
