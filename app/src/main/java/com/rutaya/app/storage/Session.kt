package com.rutaya.app.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rutaya.app.model.Account

/** Sesión opcional del usuario: token + datos básicos de la cuenta. Sin sesión, la app funciona como invitado. */
object Session {

    private const val PREF_NAME = "rutaya_session"
    private var prefs: SharedPreferences? = null

    /** Cuenta actual o null si es invitado (cambia → la interfaz se actualiza sola). */
    var account by mutableStateOf<Account?>(null)
        private set

    var token: String? = null
        private set

    val isLoggedIn: Boolean get() = account != null && token != null

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs = p
        val savedToken = p.getString("token", null)
        val id = p.getString("id", null)
        val email = p.getString("email", null)
        val name = p.getString("name", null)
        if (savedToken != null && id != null && email != null && name != null) {
            token = savedToken
            account = Account(id, email, name)
        }
    }

    fun login(newToken: String, newAccount: Account) {
        token = newToken
        account = newAccount
        prefs?.edit()
            ?.putString("token", newToken)
            ?.putString("id", newAccount.id)
            ?.putString("email", newAccount.email)
            ?.putString("name", newAccount.name)
            ?.apply()
    }

    fun updateAccount(newAccount: Account) {
        account = newAccount
        prefs?.edit()?.putString("name", newAccount.name)?.putString("email", newAccount.email)?.apply()
    }

    fun logout() {
        token = null
        account = null
        prefs?.edit()?.clear()?.apply()
    }
}
