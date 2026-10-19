package com.devbehindyou.atomicfilemanager.data.backend.network

import android.content.Context
import android.content.SharedPreferences
import com.devbehindyou.atomicfilemanager.domain.model.NetworkProtocol
import com.devbehindyou.atomicfilemanager.domain.model.NetworkServerConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Saved network servers and their passwords. Passwords are encrypted with [cipher] (AES-GCM with
 * a key in the Android Keystore, ALL_IN_ONE_PLAN.md 0.4) and never stored in plain text. Values
 * from before 0.4 are re-encrypted the first time they are read.
 */
class NetworkCredentialsStore(
    private val context: Context,
    private val cipher: PasswordCipher = AesGcmPasswordCipher.androidKeystore(),
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val legacy by lazy { LegacyPasswordReader(context.packageName) }

    fun getAllServers(): List<NetworkServerConfig> {
        val jsonStr = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val list = mutableListOf<NetworkServerConfig>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val protocolStr = obj.optString("protocol", NetworkProtocol.FTP.name)
                val protocol = runCatching { NetworkProtocol.valueOf(protocolStr) }.getOrDefault(NetworkProtocol.FTP)
                list.add(
                    NetworkServerConfig(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        protocol = protocol,
                        host = obj.getString("host"),
                        port = obj.optInt("port", protocol.defaultPort),
                        username = obj.optString("username", ""),
                        remotePath = obj.optString("remotePath", "/"),
                        anonymous = obj.optBoolean("anonymous", false),
                    ),
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getServerById(id: String): NetworkServerConfig? {
        return getAllServers().firstOrNull { it.id == id }
    }

    /** Returns false when the password could not be stored (the server itself is saved). */
    fun saveServer(
        config: NetworkServerConfig,
        password: String?,
    ): Boolean =
        synchronized(writerLock) {
            val servers = getAllServers().filter { it.id != config.id }.toMutableList()
            servers.add(config)

            val jsonArray = JSONArray()
            for (server in servers) {
                val obj =
                    JSONObject().apply {
                        put("id", server.id)
                        put("name", server.name)
                        put("protocol", server.protocol.name)
                        put("host", server.host)
                        put("port", server.port)
                        put("username", server.username)
                        put("remotePath", server.remotePath)
                        put("anonymous", server.anonymous)
                    }
                jsonArray.put(obj)
            }

            prefs.edit().putString(KEY_SERVERS, jsonArray.toString()).apply()

            password == null || savePassword(config.id, password)
        }

    fun deleteServer(id: String) =
        synchronized(writerLock) {
            val servers = getAllServers().filter { it.id != id }
            val jsonArray = JSONArray()
            for (server in servers) {
                val obj =
                    JSONObject().apply {
                        put("id", server.id)
                        put("name", server.name)
                        put("protocol", server.protocol.name)
                        put("host", server.host)
                        put("port", server.port)
                        put("username", server.username)
                        put("remotePath", server.remotePath)
                        put("anonymous", server.anonymous)
                    }
                jsonArray.put(obj)
            }
            prefs.edit()
                .putString(KEY_SERVERS, jsonArray.toString())
                .remove(KEY_PASS_PREFIX + id)
                .apply()
        }

    /**
     * Encrypts and stores [pass]. Returns false, and stores nothing, if it can't be encrypted;
     * the old plain-text fallback is gone. Any previous password for the server is removed then too.
     */
    fun savePassword(
        serverId: String,
        pass: String,
    ): Boolean {
        val sealed =
            try {
                cipher.encrypt(pass)
            } catch (_: Exception) {
                prefs.edit().remove(KEY_PASS_PREFIX + serverId).apply()
                return false
            }
        prefs.edit().putString(KEY_PASS_PREFIX + serverId, sealed).apply()
        return true
    }

    /** The password, or null when none is saved or it can't be decrypted (for example tampered). */
    fun getPassword(serverId: String): String? {
        val stored = prefs.getString(KEY_PASS_PREFIX + serverId, null) ?: return null
        if (cipher.isCurrent(stored)) return cipher.decrypt(stored)
        // Written before 0.4: read it the old way once and store it encrypted properly.
        val password = legacy.read(stored)
        if (!savePassword(serverId, password)) prefs.edit().remove(KEY_PASS_PREFIX + serverId).apply()
        return password
    }

    companion object {
        private val writerLock = Any()
        private const val PREFS_NAME = "atomic_network_credentials"
        private const val KEY_SERVERS = "saved_servers"
        private const val KEY_PASS_PREFIX = "server_pass_"
    }
}
