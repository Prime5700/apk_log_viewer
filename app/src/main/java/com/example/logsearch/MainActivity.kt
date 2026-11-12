package com.example.logsearch

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private data class AppItem(val label: String, val pkg: String, val uid: Int) {
        override fun toString() = if (pkg.isEmpty()) label else "$label\n$pkg"
    }

    private val adapter = LogAdapter()
    private var allLines: List<String> = emptyList()
    private var shownLines: List<String> = emptyList()
    private var selected: AppItem? = null   // null = all apps

    private lateinit var etSearch: EditText
    private lateinit var tvCount: TextView
    private lateinit var btnApp: Button
    private lateinit var recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etSearch = findViewById(R.id.etSearch)
        tvCount = findViewById(R.id.tvCount)
        btnApp = findViewById(R.id.btnApp)
        recycler = findViewById(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<Button>(R.id.btnLoad).setOnClickListener { loadLogs() }
        findViewById<Button>(R.id.btnSample).setOnClickListener { addSampleLogs() }
        findViewById<Button>(R.id.btnShare).setOnClickListener { shareLogs() }
        findViewById<Button>(R.id.btnCopy).setOnClickListener { copyLogs() }
        btnApp.setOnClickListener { pickApp() }

        // Close the keyboard on "search" and when scrolling, so text can be selected
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { hideKeyboard(); true } else false
        }
        recycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) hideKeyboard()
            }
        })

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyFilter()
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        etSearch.clearFocus()
    }

    private fun copyLogs() {
        if (shownLines.isEmpty()) {
            Toast.makeText(this, "Nothing to copy", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("logs", shownLines.joinToString("\n")))
        Toast.makeText(this, "Copied ${shownLines.size} lines", Toast.LENGTH_SHORT).show()
    }

    private fun hasReadLogs() =
        checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED

    /** Dialog with a search box + list of launchable installed apps. */
    private fun pickApp() {
        val pm = packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { AppItem(pm.getApplicationLabel(it).toString(), it.packageName, it.uid) }
            .sortedBy { it.label.lowercase() }
        val all = listOf(AppItem("All apps", "", -1)) + apps
        val shown = all.toMutableList()

        val listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, shown)
        val search = EditText(this).apply { hint = "Search apps…"; maxLines = 1; inputType = 1 }
        val list = ListView(this).apply { adapter = listAdapter }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 16, 32, 0)
            addView(search)
            addView(list)
        }
        val dialog = AlertDialog.Builder(this).setTitle("Select app").setView(root).create()

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val q = s.toString().trim()
                shown.clear()
                shown.addAll(all.filter {
                    it.label.contains(q, true) || it.pkg.contains(q, true)
                })
                listAdapter.notifyDataSetChanged()
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        list.setOnItemClickListener { _, _, pos, _ ->
            val item = shown[pos]
            selected = if (item.pkg.isEmpty()) null else item
            btnApp.text = "App: ${item.label} (tap to change)"
            dialog.dismiss()
            loadLogs()
        }
        dialog.show()
    }

    /** Dumps the logcat buffer, optionally limited to the selected app's UID. */
    private fun loadLogs() {
        tvCount.text = "Loading…"
        val sel = selected
        val ownLogsOnly = !hasReadLogs()
        Thread {
            val cmd = mutableListOf("logcat", "-d", "-v", "time")
            if (sel != null) { cmd += "--uid"; cmd += sel.uid.toString() }
            val result = try {
                val proc = Runtime.getRuntime().exec(cmd.toTypedArray())
                val lines = proc.inputStream.bufferedReader().readLines()
                proc.destroy()
                lines
            } catch (e: Exception) {
                listOf("Error reading logs: ${e.message}")
            }
            runOnUiThread {
                allLines = result
                applyFilter()
                if (ownLogsOnly && sel != null && sel.pkg != packageName) {
                    Toast.makeText(
                        this,
                        "READ_LOGS not granted – other apps' logs are hidden. See adb command.",
                        Toast.LENGTH_LONG
                    ).show()
                }
                if (shownLines.isNotEmpty()) recycler.scrollToPosition(shownLines.size - 1)
            }
        }.start()
    }

    private fun applyFilter() {
        val q = etSearch.text.toString().trim()
        shownLines = if (q.isEmpty()) allLines else allLines.filter { it.contains(q, ignoreCase = true) }
        adapter.submit(shownLines, q)
        val src = selected?.label ?: if (hasReadLogs()) "all apps" else "this app only"
        tvCount.text = if (q.isEmpty()) "$src: ${allLines.size} lines"
        else "$src: ${shownLines.size} of ${allLines.size} lines match “$q”"
    }

    private fun addSampleLogs() {
        Log.d("LogSearch", "Sample debug message")
        Log.i("LogSearch", "User opened settings screen")
        Log.w("LogSearch", "Slow network response: 2300ms")
        Log.e("LogSearch", "Failed to parse JSON", RuntimeException("Unexpected token"))
        Toast.makeText(this, "Sample logs written — tap Load logs", Toast.LENGTH_SHORT).show()
    }

    private fun shareLogs() {
        if (shownLines.isEmpty()) {
            Toast.makeText(this, "Nothing to share", Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "App logs")
            putExtra(Intent.EXTRA_TEXT, shownLines.joinToString("\n"))
        }
        startActivity(Intent.createChooser(send, "Share logs"))
    }
}
