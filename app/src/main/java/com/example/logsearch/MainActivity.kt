package com.example.logsearch

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private val adapter = LogAdapter()
    private var allLines: List<String> = emptyList()
    private var shownLines: List<String> = emptyList()

    private lateinit var etSearch: EditText
    private lateinit var tvCount: TextView
    private lateinit var recycler: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etSearch = findViewById(R.id.etSearch)
        tvCount = findViewById(R.id.tvCount)
        recycler = findViewById(R.id.recycler)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<Button>(R.id.btnLoad).setOnClickListener { loadLogs() }
        findViewById<Button>(R.id.btnSample).setOnClickListener { addSampleLogs() }
        findViewById<Button>(R.id.btnShare).setOnClickListener { shareLogs() }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = applyFilter()
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
    }

    /** Dumps the current logcat buffer. Without READ_LOGS this contains only this app's logs. */
    private fun loadLogs() {
        tvCount.text = "Loading…"
        Thread {
            val result = try {
                val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
                val lines = proc.inputStream.bufferedReader().readLines()
                proc.destroy()
                lines
            } catch (e: Exception) {
                listOf("Error reading logs: ${e.message}")
            }
            runOnUiThread {
                allLines = result
                applyFilter()
                if (shownLines.isNotEmpty()) recycler.scrollToPosition(shownLines.size - 1)
            }
        }.start()
    }

    private fun applyFilter() {
        val q = etSearch.text.toString().trim()
        shownLines = if (q.isEmpty()) allLines else allLines.filter { it.contains(q, ignoreCase = true) }
        adapter.submit(shownLines, q)
        tvCount.text = if (q.isEmpty()) "${allLines.size} lines"
        else "${shownLines.size} of ${allLines.size} lines match “$q”"
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
