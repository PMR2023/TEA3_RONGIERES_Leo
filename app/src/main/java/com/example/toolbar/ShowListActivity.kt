package com.example.toolbar

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat.getSystemService
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject
import java.io.File

class ShowListActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var urlAPI :String

    private lateinit var idl : String
    private lateinit var itemList: MutableList<ListItem>
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

        db = AppDatabase.getDatabase(applicationContext)

        idl = intent.getStringExtra("id").toString()

        itemList = mutableListOf()
        urlAPI = loadUrl()

        retrieveListItems(idl,urlAPI)


        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        itemAdapter = ItemListAdapter(itemList)
        recyclerView.adapter = itemAdapter

        addItemButton = findViewById(R.id.buttonAddItem)
        newItemEditText = findViewById(R.id.editTextItem)

        addItemButton.setOnClickListener {
            val newItem = newItemEditText.text.toString()
            if (newItem.isNotEmpty()) {
                addItem(newItem,urlAPI)
                newItemEditText.text.clear()
            }
        }
    }


    override fun onResume() {
        super.onResume()

        // Check for pending item updates when internet connection is restored
        syncPendingItemUpdatesWithAPI()
    }

    private fun syncPendingItemUpdatesWithAPI() {
        if (isNetworkAvailable()) {
            GlobalScope.launch {
                val pendingUpdates = db.pendingItemUpdateDao().getAllPendingItemUpdates()
                for (update in pendingUpdates) {
                    val itemId = update.itemId
                    val check = update.checked

                    putCheck(idl, itemId, check, urlAPI)
                }
            }
        }
    }

    inner class ItemListAdapter(private val itemList: MutableList<ListItem>) :
        RecyclerView.Adapter<ItemListAdapter.ItemViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_layout, parent, false)
            return ItemViewHolder(view)
        }

        override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
            val item = itemList[position]
            holder.bind(item.label)
        }

        override fun getItemCount(): Int {
            return itemList.size
        }

        inner class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView),
            View.OnClickListener {
            private val checkBox: CheckBox = itemView.findViewById(R.id.checkBoxItem)
            private val textView: TextView = itemView.findViewById(R.id.textViewItem)

            init {
                itemView.setOnClickListener(this)
            }

            fun bind(item: String) {
                textView.text = item
                val isChecked = itemList[adapterPosition].checked == "1"
                checkBox.isChecked = isChecked

                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    val checkValue = if (isChecked) "1" else "0"
                    val item = itemList[adapterPosition]
                    putCheck(idl, item.id, checkValue, urlAPI)
                }

            }

            override fun onClick(view: View) {
                val item = itemList[adapterPosition]
            }
        }
    }

    private fun retrieveListItems(idl: String, urlAPI: String) {
        if (isNetworkAvailable()) {
            // Online scenario: Fetch items from API
            val url = urlAPI + "lists/$idl/items"
            val requestQueue = Volley.newRequestQueue(this)
            val request = object : JsonObjectRequest(
                Request.Method.GET, url, null,
                Response.Listener { response ->
                    handleListItemsResponse(response)
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
            // Offline scenario: Fetch items from the local database
            GlobalScope.launch {
                val items = db.itemDao().getAllItemsByList(idl)
                itemList.addAll(items.map { ListItem(it.id, it.label, it.url, it.checked) })
                runOnUiThread {
                    itemAdapter.notifyDataSetChanged()
                }
            }
        }
    }

    private fun putCheck(idl: String, id : String, check :String, urlAPI: String) {
        if (isNetworkAvailable()) {
            // Online scenario: Fetch items from API
            val url = urlAPI + "lists/$idl/items/$id?check=$check"
            val requestQueue = Volley.newRequestQueue(this)
            val request = object : JsonObjectRequest(
                Request.Method.PUT, url, null,
                Response.Listener { _ -> },
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
            // Offline scenario: Fetch items from the local database
            GlobalScope.launch {
                val item = db.itemDao().getItemById(id)
                item.checked = check
                db.itemDao().updateItem(item)

                // Save the update in the pending item updates table
                val pendingItemUpdate = PendingItemUpdate(id, check)
                db.pendingItemUpdateDao().insertOrUpdatePendingItemUpdate(pendingItemUpdate)
            }
        }
    }


    private fun handleListItemsResponse(response: JSONObject) {
        try {
            val itemsArray = response.getJSONArray("items")
            for (i in 0 until itemsArray.length()) {
                val itemObject = itemsArray.getJSONObject(i)
                val id = itemObject.getString("id")
                val label = itemObject.getString("label")
                val url = itemObject.getString("url")
                val checked = itemObject.getString("checked")
                val listItem = ListItem(id, label, url, checked)
                itemList.add(listItem)

                // Storing the item in the database
                val itemEntity = ItemEntity(id, label, url, checked,idl)
                GlobalScope.launch {
                    db.itemDao().updateItem(itemEntity)
                }
            }

            itemAdapter.notifyDataSetChanged()

        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun addItem (label: String, urlAPI: String) {
        val url = urlAPI+"lists/$idl/items?label=$label"

        val requestQueue = Volley.newRequestQueue(this)
        val request = object : JsonObjectRequest(
            Request.Method.POST, url, null,
            Response.Listener { response -> addItemResponse(response)
            },
            Response.ErrorListener { error -> Toast.makeText(this, "API connection error: ${error.message}", Toast.LENGTH_LONG).show()
            })
        {
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                val token = getToken() // Get the identification token from preferences
                headers["hash"] = token
                return headers
            }
        }

        requestQueue.add(request)
    }

    private fun addItemResponse(response: JSONObject) {
        try {
            val itemObject = response.getJSONObject("item")
            val id = itemObject.getString("id")
            val label = itemObject.getString("label")
            val url = itemObject.getString("url")
            val checked = itemObject.getString("checked")
            val listItem = ListItem(id, label, url, checked)
            itemList.add(listItem)

            val itemEntity = ItemEntity(id, label, url, checked,idl)
            GlobalScope.launch {
                db.itemDao().insertItem(itemEntity)
            }
            recyclerView.adapter?.notifyDataSetChanged()
        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    private fun loadUrl(): String {
        val sharedPreferences = getSharedPreferences("User", Context.MODE_PRIVATE)
        return sharedPreferences.getString("url", "http://tomnab.fr/todo-api/") ?: "http://tomnab.fr/todo-api/"
    }
    private fun getToken(): String {
        val sharedPreferences = getSharedPreferences("User", Context.MODE_PRIVATE)
        return sharedPreferences.getString("token", "") ?: ""
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

}
data class ListItem(val id: String, val label: String, val url: String?, val checked: String)
