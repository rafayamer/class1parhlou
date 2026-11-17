package pk.parhlou.student.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Folder for files the student shares (data export, marked papers); covered by the FileProvider. */
fun exportsDir(context: Context): File = File(context.cacheDir, "exports").apply { mkdirs() }

/**
 * Opens the share sheet for a file the app made (exports, papers, notes). Never crashes: if the file can't be shared,
 * the student sees a short message and false is returned.
 */
fun shareFile(context: Context, file: File, mimeType: String, title: String): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).setType(mimeType).putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrElse {
    android.widget.Toast.makeText(context, "Couldn't open the PDF. Please try again.", android.widget.Toast.LENGTH_LONG).show()
    false
}

/** A PDF could not be made: tell the student instead of crashing. */
fun pdfFailed(context: Context) {
    android.widget.Toast.makeText(context, "Couldn't make the PDF. Please try again.", android.widget.Toast.LENGTH_LONG).show()
}
