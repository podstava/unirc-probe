package com.example.unircprobe

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), InputManager.InputDeviceListener {
    private lateinit var manager: InputManager
    private lateinit var output: TextView
    private val lines = ArrayList<String>()
    private val lastAxis = HashMap<String, Float>()
    private val maxLines = 5000
    private var lastMotionMs = 0L
    private val exportCode = 41

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(Context.INPUT_SERVICE) as InputManager
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16); isFocusableInTouchMode = true }
        val header = TextView(this).apply { text = "UniRC Input Probe — CH6/CH7 discovery\nKeep this app in foreground. Move LD/RD and press buttons."; textSize = 17f }
        layout.addView(header)
        val buttons = LinearLayout(this)
        buttons.addView(Button(this).apply { text = "Devices"; setOnClickListener { listDevices() } })
        buttons.addView(Button(this).apply { text = "Export"; setOnClickListener { exportLog() } })
        buttons.addView(Button(this).apply { text = "Clear"; setOnClickListener { lines.clear(); output.text = ""; lastAxis.clear() } })
        layout.addView(buttons)
        output = TextView(this).apply { textSize = 12f; typeface = android.graphics.Typeface.MONOSPACE; setTextIsSelectable(true) }
        val scroll = ScrollView(this).apply { addView(output) }
        layout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(layout)
        manager.registerInputDeviceListener(this, null)
        listDevices()
        layout.requestFocus()
    }
    override fun onDestroy() { manager.unregisterInputDeviceListener(this); super.onDestroy() }
    private fun log(message: String) {
        val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())
        lines.add("$stamp $message")
        if (lines.size > maxLines) lines.removeAt(0)
        output.text = lines.takeLast(180).joinToString("\n")
    }
    private fun listDevices() {
        log("=== Android InputDevice inventory ===")
        manager.inputDeviceIds.forEach { id ->
            val d = manager.getInputDevice(id) ?: return@forEach
            log("device id=$id name=${d.name} descriptor=${d.descriptor} sources=0x${d.sources.toString(16)}")
            d.motionRanges.forEach { r -> log("  axis=${MotionEvent.axisToString(r.axis)} min=${r.min} max=${r.max} flat=${r.flat} source=0x${r.source.toString(16)}") }
        }
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        log("KEY dev=${event.deviceId} action=${event.action} key=${KeyEvent.keyCodeToString(event.keyCode)} scan=${event.scanCode} repeat=${event.repeatCount}")
        return super.dispatchKeyEvent(event)
    }
    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        recordMotion(event)
        return super.dispatchGenericMotionEvent(event)
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Touch events are intentionally not logged to avoid drowning hardware input.
        return super.dispatchTouchEvent(event)
    }
    private fun recordMotion(event: MotionEvent) {
        val d = InputDevice.getDevice(event.deviceId) ?: return
        var changes = 0
        d.motionRanges.forEach { r ->
            if ((r.source and event.source) != r.source) return@forEach
            val v = event.getAxisValue(r.axis)
            val key = "${event.deviceId}:${r.axis}"
            val old = lastAxis[key]
            if (old == null || kotlin.math.abs(v - old) >= 0.01f) {
                lastAxis[key] = v
                log("AXIS dev=${event.deviceId} ${MotionEvent.axisToString(r.axis)}=${"%.3f".format(Locale.US, v)} action=${event.action}")
                changes++
            }
        }
        lastMotionMs = SystemClock.elapsedRealtime()
    }
    override fun onInputDeviceAdded(deviceId: Int) { log("DEVICE ADDED id=$deviceId"); listDevices() }
    override fun onInputDeviceRemoved(deviceId: Int) { log("DEVICE REMOVED id=$deviceId") }
    override fun onInputDeviceChanged(deviceId: Int) { log("DEVICE CHANGED id=$deviceId"); listDevices() }
    private fun exportLog() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "unirc-input-probe-${System.currentTimeMillis()}.txt")
        }
        startActivityForResult(i, exportCode)
    }
    @Deprecated("Legacy result API used for Android 7 compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == exportCode && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try { contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(lines.joinToString("\n")) }; log("Log exported") }
                catch (e: Exception) { log("EXPORT ERROR: ${e.message}") }
            }
        }
    }
}
