package com.dinuka.hutchwatchdog

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var settings: WatchdogSettings
    private lateinit var statusText: TextView
    private lateinit var detailText: TextView
    private lateinit var lastSuccessText: TextView
    private lateinit var attemptsText: TextView
    private lateinit var enabledSwitch: Switch
    private lateinit var customUrlInput: EditText

    private val listener: (WatchdogSnapshot) -> Unit = { snapshot ->
        runOnUiThread { render(snapshot) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = WatchdogSettings(this)
        setContentView(buildLayout())
        requestNotificationPermissionIfNeeded()
        WatchdogStore.addListener(listener)
        if (settings.enabled) {
            StallWatchdogService.start(this)
        }
    }

    override fun onDestroy() {
        WatchdogStore.removeListener(listener)
        super.onDestroy()
    }

    private fun buildLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 42, 40, 32)
            setBackgroundColor(0xFFF7F8FA.toInt())
        }

        root.addView(TextView(this).apply {
            text = "Hutch Data Stall Watchdog"
            textSize = 24f
            setTextColor(0xFF10251D.toInt())
        })

        root.addView(TextView(this).apply {
            text = "Detects mobile-data stalls and nudges Android to revalidate the cellular path."
            textSize = 14f
            setTextColor(0xFF4E5D57.toInt())
            setPadding(0, 8, 0, 28)
        })

        enabledSwitch = Switch(this).apply {
            text = "Watchdog enabled"
            textSize = 18f
            isChecked = settings.enabled
            setOnCheckedChangeListener { _, enabled ->
                settings.enabled = enabled
                if (enabled) StallWatchdogService.start(this@MainActivity) else StallWatchdogService.stop(this@MainActivity)
            }
        }
        root.addView(enabledSwitch)

        statusText = metricText("State: off")
        detailText = metricText("Status: Watchdog is off")
        lastSuccessText = metricText("Last success: never")
        attemptsText = metricText("Recovery attempts: 0")
        root.addView(statusText)
        root.addView(detailText)
        root.addView(lastSuccessText)
        root.addView(attemptsText)

        root.addView(label("Probe interval"))
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                ProbePreset.entries.map { it.label }
            )
            setSelection(ProbePreset.entries.indexOf(settings.preset))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    settings.preset = ProbePreset.entries[position]
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }
        root.addView(spinner)

        root.addView(label("Optional custom probe URL"))
        customUrlInput = EditText(this).apply {
            hint = "https://your-server.example/ping"
            setSingleLine(true)
            setText(settings.customProbeUrl)
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) settings.customProbeUrl = text.toString()
            }
        }
        root.addView(customUrlInput)

        root.addView(Button(this).apply {
            text = "Save custom URL"
            setOnClickListener { settings.customProbeUrl = customUrlInput.text.toString() }
        })

        root.addView(Button(this).apply {
            text = "Battery optimization setup"
            setOnClickListener { openBatteryOptimizationSetup() }
        })

        return root
    }

    private fun metricText(initial: String): TextView {
        return TextView(this).apply {
            text = initial
            textSize = 16f
            setTextColor(0xFF1C2F28.toInt())
            setPadding(0, 18, 0, 0)
        }
    }

    private fun label(textValue: String): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 13f
            gravity = Gravity.START
            setTextColor(0xFF596961.toInt())
            setPadding(0, 26, 0, 8)
        }
    }

    private fun render(snapshot: WatchdogSnapshot) {
        enabledSwitch.isChecked = settings.enabled
        statusText.text = "State: ${snapshot.state.name.lowercase()}"
        detailText.text = "Status: ${snapshot.lastMessage}"
        attemptsText.text = "Recovery attempts: ${snapshot.recoveryAttempts}"
        lastSuccessText.text = if (snapshot.lastSuccessAtMs == 0L) {
            "Last success: never"
        } else {
            "Last success: ${DateFormat.getDateTimeInstance().format(Date(snapshot.lastSuccessAtMs))}"
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
    }

    private fun openBatteryOptimizationSetup() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(packageName)) return
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:$packageName"))
        startActivity(intent)
    }
}
