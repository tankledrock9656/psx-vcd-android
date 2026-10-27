package org.seryo.psxvcd

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.ViewGroup
import android.database.Cursor
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

class MainActivity : Activity() {
    private data class SourceEntry(
        val relativePath: String,
        val uri: Uri,
        val mimeType: String
    )

    private lateinit var selectedText: TextView
    private lateinit var statusText: TextView
    private lateinit var convertButton: Button
    private lateinit var saveButton: Button
    private var selectedTreeUri: Uri? = null
    private var sourceFiles: List<SourceEntry> = emptyList()
    private var selectedCue: SourceEntry? = null
    private var pendingVcd: File? = null
    private var isConverting = false

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
            text = "Офлайн-конвертер PS1 BIN/CUE → VCD для POPS на PS2"
            textSize = 15f
            setPadding(0, dp(8), 0, dp(22))
        })

        content.addView(actionButton("Выбрать папку с образом") { chooseGameFolder() })
        selectedText = TextView(this).apply {
            text = "Папка не выбрана"
            textSize = 14f
            setPadding(0, dp(12), 0, dp(16))
        }
        content.addView(selectedText)
        content.addView(actionButton("Проверить выбранный CUE") { inspectSelected() })
        convertButton = actionButton("Конвертировать в VCD") { convertSelected() }
        content.addView(convertButton)
        saveButton = actionButton("Сохранить готовый VCD") { requestSaveDestination() }
        saveButton.isEnabled = false
        content.addView(saveButton)

        statusText = TextView(this).apply {
            text = "Статус: выбери папку, где лежит CUE и связанные файлы BIN"
            textSize = 14f
            setPadding(0, dp(20), 0, dp(8))
        }
        content.addView(statusText)
        content.addView(TextView(this).apply {
            text = "Образы обрабатываются локально на телефоне. Выбирай папку конкретной игры, содержащую CUE и все файлы, на которые он ссылается. MDF/MDS пока не поддерживается. Первую версию обязательно проверяем на тестовом образе перед использованием на PS2."
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

    private fun chooseGameFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_TREE)
    }

    @Deprecated("Using startActivityForResult for compatibility with the starter project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) {
            if (requestCode == REQUEST_SAVE) {
                statusText.text = "Сохранение отменено. Готовый VCD остался во временной папке приложения. Можно нажать «Сохранить готовый VCD» ещё раз."
            }
            return
        }

        if (requestCode == REQUEST_TREE) {
            val treeUri = data.data ?: return
            selectedTreeUri = treeUri
            val takeFlags = data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            try {
                contentResolver.takePersistableUriPermission(treeUri, takeFlags)
            } catch (_: SecurityException) {
                // The current picker grant is still usable even if it cannot be persisted.
            }
            statusText.text = "Читаю список файлов в папке..."
            Thread {
                try {
                    val files = listFilesRecursively(treeUri)
                    runOnUiThread {
                        sourceFiles = files
                        selectedCue = null
                        val cues = files.filter { it.relativePath.substringAfterLast('.', "").equals("cue", true) }
                        if (cues.isEmpty()) {
                            selectedText.text = "В папке не найдено файлов CUE"
                            statusText.text = "Выбери папку с CUE и файлами образа"
                            return@runOnUiThread
                        }
                        val labels = cues.map { it.relativePath }.toTypedArray()
                        AlertDialog.Builder(this)
                            .setTitle("Выбери CUE игры")
                            .setItems(labels) { _, index ->
                                selectedCue = cues[index]
                                selectedText.text = "Выбран CUE:\n${cues[index].relativePath}\nФайлов в папке: ${files.size}"
                                statusText.text = "Готово к проверке"
                            }
                            .setOnCancelListener {
                                statusText.text = "Папка выбрана. Нажми «Выбрать папку с образом», чтобы выбрать CUE позже."
                            }
                            .show()
                    }
                } catch (error: Exception) {
                    runOnUiThread {
                        statusText.text = "Не удалось прочитать папку: ${error.message ?: "ошибка доступа"}"
                    }
                }
            }.start()
        } else if (requestCode == REQUEST_SAVE) {
            val targetUri = data.data ?: return
            val source = pendingVcd
            if (source == null || !source.isFile) {
                statusText.text = "Не найден временный VCD. Выполни конвертацию ещё раз."
                saveButton.isEnabled = false
                return
            }
            statusText.text = "Сохраняю VCD в выбранное место..."
            saveButton.isEnabled = false
            Thread {
                try {
                    val output = contentResolver.openOutputStream(targetUri, "w")
                        ?: throw IOException("Не удалось открыть файл для записи")
                    output.use { target ->
                        FileInputStream(source).use { input -> input.copyTo(target, 1024 * 1024) }
                    }
                    File(cacheDir, "psx-vcd-session").deleteRecursively()
                    pendingVcd = null
                    runOnUiThread {
                        statusText.text = "Готово. VCD сохранён. Перед использованием запусти игру на POPS и проверь результат."
                        saveButton.isEnabled = false
                    }
                } catch (error: Exception) {
                    runOnUiThread {
                        statusText.text = "Не удалось сохранить файл: ${error.message ?: "ошибка записи"}"
                        saveButton.isEnabled = true
                    }
                }
            }.start()
        }
    }

    private fun listFilesRecursively(treeUri: Uri): List<SourceEntry> {
        val result = mutableListOf<SourceEntry>()
        val visited = mutableSetOf<String>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        visitDirectory(treeUri, rootId, "", result, visited)
        return result
    }

    private fun visitDirectory(
        treeUri: Uri,
        directoryId: String,
        relativeDirectory: String,
        result: MutableList<SourceEntry>,
        visited: MutableSet<String>
    ) {
        if (!visited.add(directoryId)) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, directoryId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val cursor: Cursor = contentResolver.query(childrenUri, projection, null, null, null) ?: return
        cursor.use {
            val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (it.moveToNext()) {
                val childId = it.getString(idColumn)
                val name = it.getString(nameColumn) ?: continue
                val mime = it.getString(mimeColumn) ?: "application/octet-stream"
                val relativePath = if (relativeDirectory.isEmpty()) name else "$relativeDirectory/$name"
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    visitDirectory(treeUri, childId, relativePath, result, visited)
                } else {
                    result.add(SourceEntry(
                        relativePath = relativePath,
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId),
                        mimeType = mime
                    ))
                }
            }
        }
    }

    private fun inspectSelected() {
        val cue = selectedCue
        if (cue == null) {
            showMessage("Сначала выбери папку, затем CUE-файл игры.")
            return
        }
        Thread {
            try {
                val cueText = contentResolver.openInputStream(cue.uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: throw IOException("Не удалось прочитать CUE")
                val references = findCueReferences(cueText)
                val missing = findMissingReferences(cue, references, sourceFiles)
                runOnUiThread {
                    if (references.isEmpty()) {
                        statusText.text = "В CUE не найдены строки FILE в ожидаемом формате."
                    } else if (missing.isNotEmpty()) {
                        statusText.text = "CUE ссылается на отсутствующие файлы: ${missing.joinToString(", ")}"
                    } else {
                        statusText.text = "Проверка пройдена: найдено ${references.size} ссылок FILE. Формат дорожек проверит движок при конвертации."
                    }
                }
            } catch (error: Exception) {
                runOnUiThread { statusText.text = "Ошибка проверки CUE: ${error.message ?: "неизвестная ошибка"}" }
            }
        }.start()
    }

    private fun convertSelected() {
        val cue = selectedCue
        if (cue == null) {
            showMessage("Сначала выбери папку, затем CUE-файл игры.")
            return
        }
        if (isConverting) return
        if (pendingVcd?.isFile == true) {
            showMessage("Сначала сохрани предыдущий готовый VCD или нажми кнопку сохранения.")
            saveButton.isEnabled = true
            return
        }

        val filesSnapshot = sourceFiles.toList()
        isConverting = true
        convertButton.isEnabled = false
        statusText.text = "Подготавливаю файлы и запускаю локальный движок..."

        Thread {
            try {
                val sessionDir = File(cacheDir, "psx-vcd-session")
                sessionDir.deleteRecursively()
                val inputDir = File(sessionDir, "input").apply { mkdirs() }
                val outputDir = File(sessionDir, "output").apply { mkdirs() }
                val localCue = File(inputDir, cue.relativePath)
                localCue.parentFile?.mkdirs()
                copyUriToFile(cue.uri, localCue)

                val cueText = localCue.readText(Charsets.UTF_8)
                val references = findCueReferences(cueText)
                if (references.isEmpty()) {
                    throw IOException("В CUE не найдены ссылки FILE в поддерживаемом формате")
                }
                val byPath = filesSnapshot.associateBy { it.relativePath.lowercase(Locale.ROOT) }
                val cueParent = cue.relativePath.substringBeforeLast('/', "")
                val copiedPaths = mutableSetOf(cue.relativePath.lowercase(Locale.ROOT))

                for (reference in references) {
                    val resolvedPath = normalizeRelativePath(cueParent, reference)
                        ?: throw IOException("Недопустимый путь в CUE: $reference")
                    val source = byPath[resolvedPath.lowercase(Locale.ROOT)]
                        ?: throw IOException("Не найден файл, указанный в CUE: $resolvedPath. Выбери папку с полным образом игры.")
                    if (copiedPaths.add(resolvedPath.lowercase(Locale.ROOT))) {
                        val target = File(inputDir, resolvedPath)
                        target.parentFile?.mkdirs()
                        copyUriToFile(source.uri, target)
                    }
                }

                runOnUiThread { statusText.text = "Конвертирую на телефоне. Не закрывай приложение..." }
                val nativeResult = nativeConvert(localCue.absolutePath, outputDir.absolutePath, false, false)
                if (nativeResult.startsWith("ERROR\n")) {
                    throw IOException(nativeResult.removePrefix("ERROR\n"))
                }
                if (!nativeResult.startsWith("OK\n")) {
                    throw IOException("Нативный движок вернул неожиданный ответ")
                }
                val outputFile = File(nativeResult.removePrefix("OK\n").trim())
                if (!outputFile.isFile || outputFile.length() <= 1024L * 1024L) {
                    throw IOException("Выходной VCD не найден или имеет подозрительно маленький размер")
                }
                pendingVcd = outputFile
                runOnUiThread {
                    statusText.text = "VCD создан: ${outputFile.name}. Выбери место сохранения на телефоне."
                    saveButton.isEnabled = true
                    requestSaveDestination()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    statusText.text = "Конвертация не выполнена: ${error.message ?: "неизвестная ошибка"}"
                }
            } finally {
                runOnUiThread {
                    isConverting = false
                    convertButton.isEnabled = true
                }
            }
        }.start()
    }

    private fun requestSaveDestination() {
        val source = pendingVcd
        if (source == null || !source.isFile) {
            showMessage("Сначала успешно конвертируй образ.")
            saveButton.isEnabled = false
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_TITLE, source.name)
        }
        startActivityForResult(intent, REQUEST_SAVE)
    }

    private fun copyUriToFile(uri: Uri, target: File) {
        target.parentFile?.mkdirs()
        val input = contentResolver.openInputStream(uri) ?: throw IOException("Не удалось открыть исходный файл: $uri")
        input.use { source ->
            FileOutputStream(target).use { destination -> source.copyTo(destination, 1024 * 1024) }
        }
    }

    private fun findCueReferences(text: String): List<String> {
        val pattern = Regex("""(?im)^\s*FILE\s+"([^"]+)"\s+\S+\s*$""")
        return pattern.findAll(text).map { it.groupValues[1].replace('\\', '/') }.toList()
    }

    private fun findMissingReferences(cue: SourceEntry, references: List<String>, files: List<SourceEntry>): List<String> {
        val map = files.associateBy { it.relativePath.lowercase(Locale.ROOT) }
        val parent = cue.relativePath.substringBeforeLast('/', "")
        return references.mapNotNull { reference ->
            val normalized = normalizeRelativePath(parent, reference) ?: reference
            if (map.containsKey(normalized.lowercase(Locale.ROOT))) null else normalized
        }
    }

    private fun normalizeRelativePath(baseDirectory: String, path: String): String? {
        val cleaned = path.replace('\\', '/')
        if (cleaned.startsWith('/')) return null
        val parts = mutableListOf<String>()
        val combined = if (baseDirectory.isEmpty()) cleaned else "$baseDirectory/$cleaned"
        for (part in combined.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex) else return null
                else -> parts.add(part)
            }
        }
        return if (parts.isEmpty()) null else parts.joinToString("/")
    }

    private fun showMessage(message: String) {
        AlertDialog.Builder(this).setMessage(message).setPositiveButton("Понятно", null).show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private external fun nativeConvert(
        inputCuePath: String,
        outputDirectoryPath: String,
        gapPlus: Boolean,
        gapMinus: Boolean
    ): String

    companion object {
        private const val REQUEST_TREE = 1201
        private const val REQUEST_SAVE = 1202

        init {
            System.loadLibrary("psxvcd_android")
        }
    }
}
