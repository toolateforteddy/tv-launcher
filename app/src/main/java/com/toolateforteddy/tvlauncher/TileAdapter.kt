package com.toolateforteddy.tvlauncher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TileAdapter(
    private val onClick: (Tile) -> Unit,
    private val onLongClick: (Tile) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    var rows: List<Row> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = if (rows[position] is Header) TYPE_HEADER else TYPE_TILE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            object : RecyclerView.ViewHolder(inflater.inflate(R.layout.item_header, parent, false)) {}
        } else {
            TileHolder(inflater.inflate(R.layout.item_tile, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Header -> (holder.itemView as TextView).text = row.title
            is Tile -> (holder as TileHolder).bind(row)
        }
    }

    private inner class TileHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val banner: ImageView = view.findViewById(R.id.banner)
        private val label: TextView = view.findViewById(R.id.label)

        init {
            view.setOnFocusChangeListener { v, hasFocus ->
                val scale = if (hasFocus) 1.08f else 1f
                v.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
                v.elevation = if (hasFocus) 8f else 0f
            }
        }

        fun bind(tile: Tile) {
            if (tile.banner != null) {
                banner.setImageDrawable(tile.banner)
                banner.setPadding(0, 0, 0, 0)
                label.visibility = View.GONE
            } else {
                // No banner: show the icon small in the middle of the tile, with the name under it.
                banner.setImageDrawable(tile.icon)
                val pad = itemView.resources.displayMetrics.density * 20
                banner.setPadding(0, pad.toInt(), 0, pad.toInt())
                label.visibility = View.VISIBLE
                label.text = tile.label
            }
            itemView.contentDescription = tile.label
            itemView.setOnClickListener { onClick(tile) }
            itemView.setOnLongClickListener { onLongClick(tile); true }
        }
    }

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_TILE = 1
    }
}
