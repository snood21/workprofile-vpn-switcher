package io.github.snood21.workprofilevpnswitcher.util

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Простое файловое логирование в каталог приложения (context.filesDir).
 * Не требует дополнительных разрешений — каталог приватный для приложения.
 * Один файл, перезаписывается с усечением по размеру (см. trimFile).
 *
 * Writer держится открытым между вызовами write() (буферизация) — это важно
 * при активном polling-мониторинге, где запись может происходить каждые
 * несколько секунд на протяжении долгого времени, пока активен VPN.
 * Явный flush() вызывается:
 *  - всегда сразу для уровня "E" (ошибки — потеря недопустима);
 *  - не чаще раза в FLUSH_INTERVAL_MS для остальных уровней.
 * Это ограничивает риск потери данных при аварийном завершении процесса
 * несколькими последними отладочными строками, сохраняя основной выигрыш
 * от буферизации по сравнению с открытием файла на каждую запись.
 */
object FileLogger {
    private const val FILE_NAME = "app.log"
    private const val FLUSH_INTERVAL_MS = 10_000L
    private val lock = Any()
    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private const val NEWLINE_BYTE: Byte = '\n'.code.toByte()

    private var writer: BufferedWriter? = null
    private var lastFlushTime = 0L

    fun write(context: Context, tag: String, level: String, message: String) {
        val settings = AppSettings(context)
        synchronized(lock) {
            val file = File(context.filesDir, FILE_NAME)
            val maxBytes = settings.logMaxSizeKb * 1024L
            if (file.exists() && file.length() > maxBytes) {
                trimFile(file, maxBytes)
            }

            val timestamp = timestampFormat.format(Date())
            val activeWriter = getOrOpenWriter(file)
            activeWriter.write("$timestamp $level/$tag: $message\n")

            val now = System.currentTimeMillis()
            if (level == "E" || now - lastFlushTime >= FLUSH_INTERVAL_MS) {
                activeWriter.flush()
                lastFlushTime = now
            }
        }
    }

    fun readLog(context: Context): String {
        synchronized(lock) {
            writer?.flush()
        }
        val file = File(context.filesDir, FILE_NAME)
        return if (file.exists()) file.readText() else ""
    }

    fun clearLog(context: Context) {
        synchronized(lock) {
            closeWriter()
            File(context.filesDir, FILE_NAME).delete()
        }
    }

    /**
     * Закрыть writer и сбросить буфер на диск. Вызывать при остановке
     * мониторинга/сервиса, чтобы не держать файловый дескриптор открытым
     * без необходимости и гарантированно сохранить накопленные строки.
     */
    fun close() {
        synchronized(lock) {
            closeWriter()
        }
    }

    private fun getOrOpenWriter(file: File): BufferedWriter {
        writer?.let { return it }
        val newWriter = BufferedWriter(FileWriter(file, true))
        writer = newWriter
        return newWriter
    }

    private fun closeWriter() {
        try {
            writer?.flush()
            writer?.close()
        } catch (_: Exception) {
            // Файл мог быть уже удалён/недоступен — закрытие best-effort
        }
        writer = null
    }

    /**
     * Оставляет примерно половину лимита от конца файла, выравнивая срез по началу строки.
     * Запас в половину лимита нужен, чтобы усечение не выполнялось на каждой записи
     * при работе около границы размера.
     */
    private fun trimFile(file: File, maxBytes: Long) {
        // Writer держит собственный дескриптор на файл — закрываем перед
        // побайтовой перезаписью и откроем заново при следующей записи.
        closeWriter()
        val bytes = file.readBytes()
        val keepFrom = (bytes.size - maxBytes / 2).coerceAtLeast(0).toInt()
        val newlineIndex = indexOfNewline(bytes, keepFrom)
        val cutAt = if (newlineIndex >= 0) newlineIndex + 1 else keepFrom
        file.writeBytes(bytes.copyOfRange(cutAt, bytes.size))
    }

    private fun indexOfNewline(bytes: ByteArray, startIndex: Int): Int {
        for (i in startIndex until bytes.size) {
            if (bytes[i] == NEWLINE_BYTE) return i
        }
        return -1
    }
}
