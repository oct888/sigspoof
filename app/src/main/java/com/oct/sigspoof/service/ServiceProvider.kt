package com.oct.sigspoof.service

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import com.oct.sigspoof.utils.Log

class ServiceProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

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
        val callingUid = Binder.getCallingUid()
        val pkg = callingPackage
        Log.d("ServiceProvider.call: pkg=$pkg, uid=$callingUid")

        // Only accept calls from system (android package or Process.SYSTEM_UID 1000)
        if (callingUid != Process.SYSTEM_UID && pkg != "android") {
            Log.w("Rejected call from: $pkg, uid: $callingUid")
            return null
        }
        
        val binder = extras?.getBinder("binder")
        if (binder == null) {
            Log.w("No binder in extras")
            return null
        }
        
        Log.i("Received binder from system")
        ServiceClient.linkService(binder)
        return Bundle()
    }
}
