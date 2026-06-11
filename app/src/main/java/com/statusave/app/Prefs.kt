package com.statusave.app

import android.content.Context

/** Preferencias persistentes: URIs concedidos por SAF y nombre de la carpeta destino. */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("statusave", Context.MODE_PRIVATE)

    /** URI del árbol de la carpeta .Statuses de WhatsApp concedida por el usuario. */
    var statusesTreeUri: String?
        get() = sp.getString("statuses_tree_uri", null)
        set(value) = sp.edit().putString("statuses_tree_uri", value).apply()

    /** URI del árbol de la carpeta base elegida por el usuario como destino. */
    var destTreeUri: String?
        get() = sp.getString("dest_tree_uri", null)
        set(value) = sp.edit().putString("dest_tree_uri", value).apply()

    /** Subcarpeta que se crea dentro de la carpeta base (vacío = guardar directo en la base). */
    var folderName: String
        get() = sp.getString("folder_name", "StatuSave") ?: "StatuSave"
        set(value) = sp.edit().putString("folder_name", value.trim()).apply()
}
