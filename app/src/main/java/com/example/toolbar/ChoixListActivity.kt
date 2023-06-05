package com.example.toolbar

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson

class ChoixListActivity : AppCompatActivity() {
    // Variables pour stocker les données
    private lateinit var username: String
    private lateinit var listData: ListData

    // Vues dans l'activité
    private lateinit var recyclerView: RecyclerView
    private lateinit var addButton: Button
    private lateinit var newListEditText: EditText

    // SharedPreferences et Gson pour sauvegarder et charger les données
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var gson: Gson

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_choix_list)

        setSupportActionBar(findViewById(R.id.toolbar))

        // Initialisation des SharedPreferences et Gson
        sharedPreferences = getSharedPreferences("ListData", Context.MODE_PRIVATE)
        gson = Gson()

        // Récupération du nom d'utilisateur et des données de liste
        username = intent.getStringExtra("username").toString()
        listData = loadData()

        // Configuration du RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = ItemListAdapter(listData.itemLists)

        // Configuration du bouton pour ajouter une nouvelle liste
        addButton = findViewById(R.id.buttonAddList)
        newListEditText = findViewById(R.id.editTextListName)

        addButton.setOnClickListener {
            val newListName = newListEditText.text.toString()
            if (newListName.isNotEmpty()) {
                addNewList(newListName)
                newListEditText.text.clear()
            }
        }
    }

    // Méthode pour ajouter une nouvelle liste
    private fun addNewList(listName: String) {
        val newList = ListData.ItemList(listName, emptyList())
        listData.itemLists.add(newList)
        saveData(listData)
        recyclerView.adapter?.notifyDataSetChanged()
    }

    // Méthode pour sauvegarder les données dans les SharedPreferences
    private fun saveData(data: ListData) {
        val jsonData = gson.toJson(data)
        val editor = sharedPreferences.edit()
        editor.putString(username, jsonData)
        editor.apply()
    }

    // Méthode pour charger les données depuis les SharedPreferences
    private fun loadData(): ListData {
        val jsonData = sharedPreferences.getString(username, null)
        return if (jsonData != null) {
            gson.fromJson(jsonData, ListData::class.java)
        } else {
            ListData(mutableListOf())
        }
    }

    // Adaptateur pour le RecyclerView
    inner class ItemListAdapter(private val itemLists: MutableList<ListData.ItemList>) :
        RecyclerView.Adapter<ItemListAdapter.ItemListViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemListViewHolder {
            // Création de la vue pour chaque élément de la liste
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return ItemListViewHolder(view)
        }

        override fun onBindViewHolder(holder: ItemListViewHolder, position: Int) {
            val itemList = itemLists[position]
            holder.bind(itemList)
        }

        override fun getItemCount(): Int {
            return itemLists.size
        }

        inner class ItemListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
            View.OnClickListener {

            private val textView: TextView = itemView.findViewById(android.R.id.text1)

            init {
                itemView.setOnClickListener(this)
            }

            fun bind(itemList: ListData.ItemList) {
                textView.text = itemList.name
            }

            override fun onClick(view: View) {
                val itemList = itemLists[adapterPosition]

                val intent = Intent(this@ChoixListActivity, ShowListActivity::class.java)
                intent.putExtra("username", username)
                intent.putExtra("listName", itemList.name)
                startActivity(intent)
            }
        }
    }
}

// Modèle de données pour les listes
data class ListData(var itemLists: MutableList<ItemList>) {
    data class ItemList(val name: String, val items: List<String>)
}
