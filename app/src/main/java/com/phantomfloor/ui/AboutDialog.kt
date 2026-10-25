package com.phantomfloor.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.widget.Toast
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phantomfloor.BuildConfig
import com.phantomfloor.R
import com.phantomfloor.databinding.DialogAboutBinding
import com.phantomfloor.util.UpdateChecker

/**
 * "About & help": version and update status, quick setup steps, links to itch.io and GitHub,
 * a problem report pre-filled with diagnostics, privacy, credits, and the support button.
 * Long-press the header to copy the diagnostics.
 */
object AboutDialog {

    fun show(context: Context, update: UpdateChecker.Status, diagnostics: String, openUrl: (String) -> Unit) {
        val b = DialogAboutBinding.inflate(LayoutInflater.from(context))
        b.aboutVersion.text = context.getString(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        b.aboutUpdate.setOnClickListener(null)
        when (update) {
            UpdateChecker.Status.Checking -> b.aboutUpdate.setText(R.string.about_update_checking)
            UpdateChecker.Status.UpToDate -> b.aboutUpdate.setText(R.string.about_up_to_date)
            UpdateChecker.Status.Unknown -> b.aboutUpdate.setText(R.string.about_update_unknown)
            is UpdateChecker.Status.Available -> {
                b.aboutUpdate.text = context.getString(R.string.about_update_available, update.update.version)
                b.aboutUpdate.setTextColor(0xFF7CFF9B.toInt())
                b.aboutUpdate.setOnClickListener { openUrl(update.update.url) }
            }
        }

        val repo = BuildConfig.UPDATE_REPO
        b.aboutItch.setOnClickListener { openUrl(BuildConfig.SUPPORT_URL) }
        b.aboutGithub.isVisible = repo.isNotBlank()
        b.aboutGithub.setOnClickListener { openUrl("https://github.com/$repo") }
        b.aboutReport.isVisible = repo.isNotBlank()
        b.aboutReport.setOnClickListener { openUrl(issueUrl(repo, diagnostics)) }
        b.aboutSupport.setOnClickListener { openUrl(BuildConfig.SUPPORT_URL) }
        b.aboutHeader.setOnLongClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("PhantomFloor diagnostics", diagnostics))
            Toast.makeText(context, R.string.about_diagnostics_copied, Toast.LENGTH_SHORT).show()
            true
        }

        MaterialAlertDialogBuilder(context)
            .setView(b.root)
            .setPositiveButton(R.string.about_close, null)
            .show()
    }

    /** A new GitHub issue with the diagnostics already filled in. */
    fun issueUrl(repo: String, diagnostics: String): String {
        val body = "**What happened?**\n\n\n**What did you expect?**\n\n\n**Diagnostics**\n```\n$diagnostics\n```\n"
        return "https://github.com/$repo/issues/new?title=${Uri.encode("Problem: ")}&body=${Uri.encode(body)}"
    }
}
