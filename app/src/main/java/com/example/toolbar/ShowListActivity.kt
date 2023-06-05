package com.example.toolbar

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

class ShowListActivity : AppCompatActivity() {
    private lateinit var username: String
    private lateinit var listName: String
    private lateinit var itemList: MutableList<String>
    private lateinit var itemAdapter: ItemListAdapter

    private lateinit var recyclerView: RecyclerView
    private lateinit var addItemButton: Button
    private lateinit var newItemEditText: EditText

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var gson: Gson

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_show_list)
        setSupportActionBar(findViewById(R.id.toolbar))

        // Initialisation des SharedPreferences
        sharedPreferences = getSharedPreferences("ListData", Context.MODE_PRIVATE)
        gson = Gson()

        username = intent.getStringExtra("username").toString()
        listName = intent.getStringExtra("listName").toString()
        itemList = loadItemList()

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        itemAdapter = ItemListAdapter(itemList)
        recyclerView.adapter = itemAdapter

        addItemButton = findViewById(R.id.buttonAddItem)
        newItemEditText = findViewById(R.id.editTextItem)

        addItemButton.setOnClickListener {
            val newItem = newItemEditText.text.toString().trim()
            if (newItem.isNotEmpty()) {
                itemList.add(newItem)
                saveItemList()
                itemAdapter.notifyDataSetChanged()
                newItemEditText.text.clear()
            }
        }
    }

    private fun saveItemList() {
        // Convertir la liste d'articles en JSON
        val jsonData = gson.toJson(itemList)
        val editor = sharedPreferences.edit()
        editor.putString("$username-$listName", jsonData)
        editor.apply()
    }

    private fun loadItemList(): MutableList<String> {
        val jsonData = sharedPreferences.getString("$username-$listName", null)
        return if (jsonData != null) {
            // Convertir le JSON en liste d'articles
            gson.fromJson(jsonData, object : TypeToken<MutableList<String>>() {}.type)
        } else {
            mutableListOf()
        }
    }

    inner class ItemListAdapter(private val itemList: MutableList<String>) :
        RecyclerView.Adapter<ItemListAdapter.ItemViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_layout, parent, false)
            return ItemViewHolder(view)
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            val item = itemList[position]
            holder.bind(item)
        }

        override fun getItemCount(): Int {
            return itemList.size
        }

        inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
            View.OnClickListener {

            private val textView: TextView = itemView.findViewById(R.id.textViewItem)

            init {
                itemView.setOnClickListener(this)
            }

            fun bind(item: String) {
                textView.text = item
            }

            override fun onClick(view: View) {
                val item = itemList[adapterPosition]
            }
        }
    }
}
