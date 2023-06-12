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
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import org.json.JSONException
import org.json.JSONObject

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
        val urlAPI = loadUrl()

        retrieveUserLists(urlAPI)
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
                //addNewList(newListName)
                addList(newListName,urlAPI)
                newListEditText.text.clear()
            }
        }

    }

    // Méthode pour ajouter une nouvelle liste
    private fun addNewList(listName: String , id: String) {
        val newList = ListData.ItemList(id, listName)
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
                intent.putExtra("id", itemList.id)
                startActivity(intent)
            }
        }
    }

    private fun retrieveUserLists(urlAPI: String) {
        val url = urlAPI+"lists"

        val requestQueue = Volley.newRequestQueue(this)
        val request = object : JsonObjectRequest(
            Request.Method.GET, url, null,
            Response.Listener { response ->
                System.out.println(response.toString())
                handleUserListsResponse(response)
            },
            Response.ErrorListener { error ->
                Toast.makeText(this, "API connection error: ${error.message}", Toast.LENGTH_LONG).show()
                System.out.println(error.message)
            }) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                val token = getToken() // Get the identification token from preferences
                headers["hash"] = token
                return headers
            }
        }

        requestQueue.add(request)
    }

    private fun getToken(): String {
        val sharedPreferences = getSharedPreferences("User", Context.MODE_PRIVATE)
        return sharedPreferences.getString("token", "") ?: ""
    }

    // Méthode pour traiter la réponse contenant les listes de l'utilisateur
    private fun handleUserListsResponse(response: JSONObject) {
        try {
            // Clear existing item lists
            listData.itemLists.clear()

            // Parse the JSON response and create ListData objects
            val listsArray = response.getJSONArray("lists")
            for (i in 0 until listsArray.length()) {
                val listObject = listsArray.getJSONObject(i)
                val id = listObject.getString("id")
                val label = listObject.getString("label")
                val itemList = ListData.ItemList(id, label)
                listData.itemLists.add(itemList)
            }

            // Save the updated data and update the RecyclerView adapter
            saveData(listData)
            recyclerView.adapter?.notifyDataSetChanged()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun loadUrl(): String {
        val sharedPreferences = getSharedPreferences("User", Context.MODE_PRIVATE)
        return sharedPreferences.getString("url", "http://tomnab.fr/todo-api/") ?: "http://tomnab.fr/todo-api/"
    }

    private fun addList (label: String, urlAPI: String) {
        val url = urlAPI+"lists?label=$label"

        val requestQueue = Volley.newRequestQueue(this)
        val request = object : JsonObjectRequest(
            Request.Method.POST, url, null,
            Response.Listener { response ->
                addListResponse(response)
            },
            Response.ErrorListener { error ->
                Toast.makeText(this, "API connection error: ${error.message}", Toast.LENGTH_LONG).show()
                System.out.println(error.message)
            }) {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                val token = getToken() // Get the identification token from preferences
                headers["hash"] = token
                return headers
            }
        }

        requestQueue.add(request)
    }
    private fun addListResponse(response: JSONObject) {
        try {
            val data = response.getJSONObject("list")
            val id = data.getString("id")
            val label = data.getString("label")
            val itemList = ListData.ItemList(id, label)
            listData.itemLists.add(itemList)

            saveData(listData)
            recyclerView.adapter?.notifyDataSetChanged()

        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

}

// Modèle de données pour les listes
data class ListData(var itemLists: MutableList<ItemList>) {
    data class ItemList(val id: String, val name: String)
}
