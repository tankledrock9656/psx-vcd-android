package org.seryo.psxvcd

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var selectedText: TextView
    private lateinit var statusText: TextView
    private var selectedUri: Uri? = null
    private var selectedName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(22))
            gravity = Gravity.TOP
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(18))
        }
        scroll.addView(content)

        content.addView(TextView(this).apply {
            text = "PSX VCD Converter"
            textSize = 26f
            setTextColor(0xFF172033.toInt())
        })
        content.addView(TextView(this).apply {
            text = "Образы PlayStation 1 → VCD для POPS на PS2"
            textSize = 15f
            setPadding(0, dp(8), 0, dp(22))
        })

        content.addView(actionButton("Выбрать CUE или MDS") { chooseImage() })
        selectedText = TextView(this).apply {
            text = "Образ не выбран"
            textSize = 14f
            setPadding(0, dp(12), 0, dp(16))
        }
        content.addView(selectedText)

        content.addView(actionButton("Проверить поддержку формата") { inspectSelected() })
        content.addView(actionButton("Конвертировать в VCD") { explainEngineStatus() })

        statusText = TextView(this).apply {
            text = "Статус: ожидается выбор образа"
            textSize = 14f
            setPadding(0, dp(20), 0, dp(8))
        }
        content.addView(statusText)

        content.addView(TextView(this).apply {
            text = "Важно: это ранняя версия интерфейса. Движок конвертации ещё не встроен. Не используйте этот APK для конвертации образов. Поддержку MDF/MDS и сохранение многодорожечного аудио нужно реализовать и проверить отдельно."
            textSize = 13f
            setTextColor(0xFF8A3A20.toInt())
            setPadding(0, dp(18), 0, dp(8))
        })

        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun chooseImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/octet-stream", "application/x-cue", "application/x-mds"
            ))
        }
        startActivityForResult(intent, PICK_IMAGE)
    }

    @Deprecated("Using startActivityForResult for compatibility with the starter scaffold")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        selectedUri = uri
        selectedName = uri.lastPathSegment?.substringAfterLast('/') ?: "Выбранный файл"
        selectedText.text = "Выбран файл:\n$selectedName"
        statusText.text = "Статус: файл выбран"
    }

    private fun inspectSelected() {
        val name = selectedName
        if (selectedUri == null || name == null) {
            showMessage("Сначала выбери файл CUE или MDS.")
            return
        }
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        statusText.text = when (ext) {
            "cue" -> "CUE выбран. Проверка дорожек и запуск конвертера появятся после интеграции движка."
            "mds" -> "MDS выбран. MDF/MDS пока не поддерживается движком."
            "bin" -> "BIN сам по себе недостаточен для надёжной конвертации. Выбери соответствующий CUE."
            else -> "Формат .$ext не распознан. Выбери CUE или MDS."
        }
    }

    private fun explainEngineStatus() {
        AlertDialog.Builder(this)
            .setTitle("Движок пока не встроен")
            .setMessage("Сейчас это стартовый проект интерфейса и сборки APK. Конвертация ещё не реализована, чтобы приложение не создавало повреждённые VCD-файлы.")
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun showMessage(message: String) {
        AlertDialog.Builder(this).setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val PICK_IMAGE = 1201
    }
}
