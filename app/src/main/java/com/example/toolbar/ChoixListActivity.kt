package com.example.toolbar

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.room.Room

import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import org.json.JSONException
import org.json.JSONObject
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class ChoixListActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase

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

        db = AppDatabase.getDatabase(applicationContext)
        // Initialisation des SharedPreferences et Gson
        sharedPreferences = getSharedPreferences("ListData", Context.MODE_PRIVATE)
        gson = Gson()

        // Récupération du nom d'utilisateur et des données de liste
        username = intent.getStringExtra("username").toString()
        val urlAPI = loadUrl()

        listData=ListData(mutableListOf())
        retrieveUserLists(urlAPI)


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

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        if (connectivityManager != null) {
            val capabilities =
                connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return true
                } else if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    return true
                }
            }
        }
        return false
    }

    private fun retrieveUserLists(urlAPI: String) {

        if (isNetworkAvailable()) {
            // L'utilisateur dispose d'une connexion Internet, exécuter la requête Volley
            val url = urlAPI+"lists"

            val requestQueue = Volley.newRequestQueue(this)
            val request = object : JsonObjectRequest(
                Method.GET, url, null,
                Response.Listener { response ->
                    handleUserListsResponse(response)
                },
                Response.ErrorListener { error ->
                }) {
                override fun getHeaders(): MutableMap<String, String> {
                    val headers = HashMap<String, String>()
                    val token = getToken() // Get the identification token from preferences
                    headers["hash"] = token
                    return headers
                }
            }

            requestQueue.add(request)
        } else {
            // L'utilisateur n'a pas de connexion Internet, charger les listes à partir de la base de données locale
            loadUserListsFromDatabase()
        }

    }

    private fun getToken(): String {
        val sharedPreferences = getSharedPreferences("User", Context.MODE_PRIVATE)
        return sharedPreferences.getString("token", "") ?: ""
    }

    // Méthode pour traiter la réponse contenant les listes de l'utilisateur
    @SuppressLint("NotifyDataSetChanged")
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

                val listEntity = ListEntity(id, label, username)
                GlobalScope.launch {
                    db.listDao().insertList(listEntity)
                }
            }

            // Save the updated data and update the RecyclerView adapter
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
            Method.POST, url, null,
            Response.Listener { response ->
                addListResponse(response)
            },
            Response.ErrorListener { error ->
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
    @SuppressLint("NotifyDataSetChanged")
    private fun addListResponse(response: JSONObject) {
        try {
            val data = response.getJSONObject("list")
            val id = data.getString("id")
            val label = data.getString("label")
            val itemList = ListData.ItemList(id, label)
            listData.itemLists.add(itemList)

            recyclerView.adapter?.notifyDataSetChanged()

        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun loadUserListsFromDatabase() {
        GlobalScope.launch(Dispatchers.Main) {
            val lists = withContext(Dispatchers.IO) {
                db.listDao().getAllListsByUser(username)
            }
            listData.itemLists.clear()
            listData.itemLists.addAll(lists.map { ListData.ItemList(it.id, it.label) })
            recyclerView.adapter?.notifyDataSetChanged()
        }
    }


}

// Modèle de données pour les listes
data class ListData(var itemLists: MutableList<ItemList>) {
    data class ItemList(val id: String, val name: String)
}



