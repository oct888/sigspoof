package com.oct.sigspoof.service

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import com.oct.sigspoof.utils.Log

class ServiceProvider : ContentProvider() {

    override fun onCreate(): Boolean = false

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ) = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        // Only accept calls from system (android package)
        if (callingPackage != "android" || extras == null) {
            Log.w("Rejected call from: $callingPackage")
            return null
        }
        
        val binder = extras.getBinder("binder")
        if (binder == null) {
            Log.w("No binder in extras")
            return null
        }
        
        Log.i("Received binder from system")
        ServiceClient.linkService(binder)
        return Bundle()
    }
}
