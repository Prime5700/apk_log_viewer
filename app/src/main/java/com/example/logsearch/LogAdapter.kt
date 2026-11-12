package com.example.logsearch

import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

    private var items: List<String> = emptyList()
    private var query: String = ""

    class VH(val tv: TextView) : RecyclerView.ViewHolder(tv)

    fun submit(lines: List<String>, query: String) {
        this.items = lines
        this.query = query
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(8, 4, 8, 4)
            setTextIsSelectable(true)
        }
        return VH(tv)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val line = items[position]
        val span = SpannableString(line)

        // Highlight every match of the query (case-insensitive)
        if (query.isNotEmpty()) {
            var idx = line.indexOf(query, 0, ignoreCase = true)
            while (idx >= 0) {
                span.setSpan(
                    BackgroundColorSpan(Color.YELLOW), idx, idx + query.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                idx = line.indexOf(query, idx + query.length, ignoreCase = true)
            }
        }
        holder.tv.text = span
        holder.tv.setTextColor(levelColor(line))
    }

    // logcat "-v time" format: "MM-DD HH:MM:SS.mmm L/Tag(pid): msg" -> level char at index 19
    private fun levelColor(line: String): Int =
        when (line.getOrNull(19)) {
            'E', 'F' -> Color.parseColor("#E53935")
            'W' -> Color.parseColor("#FB8C00")
            'I' -> Color.parseColor("#43A047")
            'D' -> Color.parseColor("#1E88E5")
            else -> Color.GRAY
        }
}
