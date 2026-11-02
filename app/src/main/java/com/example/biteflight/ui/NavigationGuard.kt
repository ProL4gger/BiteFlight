package com.example.biteflight.ui

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

interface NavigationGuard {
    fun hasUnsavedChanges(): Boolean
    fun pendingOperationMessage(): String? = null
}

object GuardedNavigation {
    private const val DIALOG_TAG = "unsaved_changes_dialog"

    fun leave(activity: AppCompatActivity, action: () -> Unit) {
        val guard = activity as? NavigationGuard
        guard?.pendingOperationMessage()?.let {
            Toast.makeText(activity, it, Toast.LENGTH_SHORT).show()
            return
        }
        if (guard?.hasUnsavedChanges() != true) {
            action()
            return
        }
        // A repeated swipe or tap must not open multiple confirmation dialogs.
        val root = activity.window.decorView
        if (root.getTag(com.example.biteflight.R.id.unsavedChangesDialog) == DIALOG_TAG) return
        root.setTag(com.example.biteflight.R.id.unsavedChangesDialog, DIALOG_TAG)
        AlertDialog.Builder(activity)
            .setTitle("Discard unsaved changes?")
            .setMessage("Stay here to save your changes, or discard them and leave.")
            .setNegativeButton("Stay", null)
            .setPositiveButton("Discard") { _, _ -> action() }
            .setOnDismissListener { root.setTag(com.example.biteflight.R.id.unsavedChangesDialog, null) }
            .show()
    }
}
