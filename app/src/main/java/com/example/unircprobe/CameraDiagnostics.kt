package com.example.unircprobe

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.text.InputType
import android.widget.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.Locale
import java.util.concurrent.Executors

private enum class Query(val label: String) {
    TAS("Поточне значення шторки"), VER("Версія прошивки"),
    HWV("Апаратна версія"), MOD("Код моделі"), REC("Стан запису");

    override fun toString() = label

    fun explain(data: String): String = when (this) {
        TAS -> data.toIntOrNull(16)?.let { "Значення шторки: $it (одиниці не підтверджено)" }
        REC -> when (data) {
            "00" -> "Запис зупинено"
            "01" -> "Запис триває"
            else -> null
        }
        else -> null
    } ?: "$label: код «$data»"
}

private fun checksum(body: String) =
    String.format(Locale.US, "%02X", body.sumOf { it.code } and 255)

private fun readCamera(host: String, query: Query, sent: (String) -> Unit): String {
    val address = InetAddress.getByName(host)
    val body = "#TPUD2r${query.name}00"
    val frame = body + checksum(body)
    val bytes = (frame + "\r\n").toByteArray(Charsets.US_ASCII)
    DatagramSocket(5000).use { socket ->
        socket.connect(address, 5000)
        socket.send(DatagramPacket(bytes, bytes.size, address, 5000))
        sent(frame)
        val buffer = ByteArray(65507)
        val deadline = SystemClock.elapsedRealtime() + 2500
        var last = ""
        val header = Regex("#TPDU([0-9A-Fa-f])r${query.name}", RegexOption.IGNORE_CASE)
        while (!Thread.currentThread().isInterrupted) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            if (remaining <= 0) break
            socket.soTimeout = remaining.toInt()
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) { break }
            val raw = String(buffer, 0, packet.length, Charsets.US_ASCII)
            last = raw.take(160)
            for (match in header.findAll(raw)) {
                val start = match.range.first
                val end = start + 10 + match.groupValues[1].toInt(16)
                if (end + 2 > raw.length) continue
                val value = raw.substring(start + 10, end)
                if (value.isEmpty()) continue
                if (!checksum(raw.substring(start, end)).equals(raw.substring(end, end + 2), true)) continue
                return "${query.explain(value)}\nRX  ${raw.substring(start, end + 2)}"
            }
        }
        return if (last.isEmpty()) "За 2,5 с відповідь не надійшла."
        else "Пакет отримано, але коректної відповіді на ${query.name} немає.\nОстанній пакет: $last"
    }
}

class CameraDiagnostics : Activity() {
    private val worker = Executors.newSingleThreadExecutor()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val prefs = getSharedPreferences("camera", MODE_PRIVATE)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        val host = EditText(this).apply {
            hint = "IP камери"; setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.getString("host", "192.168.144.108"))
        }
        val picker = Spinner(this).apply {
            adapter = ArrayAdapter(this@CameraDiagnostics, android.R.layout.simple_spinner_dropdown_item, Query.values())
        }
        val result = TextView(this).apply { setTextIsSelectable(true); textSize = 17f }
        val send = Button(this).apply { text = "Прочитати з камери" }
        box.addView(TextView(this).apply { text = "Діагностика C12 · IP камери · UDP 5000" })
        for (view in listOf(host, picker, send, result)) box.addView(view)
        setContentView(ScrollView(this).apply { addView(box) })
        send.setOnClickListener {
            val ip = host.text.toString().trim()
            if (!android.util.Patterns.IP_ADDRESS.matcher(ip).matches()) {
                host.error = "Введи IP-адресу камери"
                return@setOnClickListener
            }
            prefs.edit().putString("host", ip).apply()
            val query = picker.selectedItem as Query
            send.isEnabled = false
            result.text = "${query.label}\n"
            worker.execute {
                val reply = try {
                    readCamera(ip, query) { tx -> runOnUiThread { result.append("TX  $tx\n") } }
                } catch (e: java.net.BindException) {
                    "Локальний порт 5000 зайнятий. Закрий інший застосунок камери й повтори."
                } catch (e: Exception) { "Помилка зв’язку: ${e.localizedMessage}" }
                runOnUiThread { if (!isDestroyed) { result.append(reply); send.isEnabled = true } }
            }
        }
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
