package com.example.ftpclient.download

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.ftpclient.R

private const val TAG = "==>>FileAdapter"
class FileAdapter(private val fileList: List<FileItem>,private val func:(fileItems: FileItem)->Unit) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val fileNameTextView: TextView = itemView.findViewById(R.id.fileNameTextView)
        fun bindUI(fileItem: FileItem, fileList: List<FileItem>, position: Int) {
            fileNameTextView.setOnClickListener { 
                val fileItems = this@FileAdapter.fileList[adapterPosition]
                Log.d(TAG, "bindUI: $fileItems")
                func(fileItems)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val fileItem = fileList[position]
        holder.fileNameTextView.text = fileItem.name
        holder.bindUI(fileItem,fileList,position)
    }

    override fun getItemCount(): Int = fileList.size
}
