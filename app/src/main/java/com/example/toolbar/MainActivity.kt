package com.example.toolbar

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var db: AppDatabase
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))

        db = AppDatabase.getDatabase(applicationContext)
        // Initialisation des SharedPreferences
        sharedPreferences = getSharedPreferences("User", Context.MODE_PRIVATE)

        editTextUsername = findViewById(R.id.editTextTextUserName)
        editTextPassword = findViewById(R.id.editTextTextPassword)
        val buttonOK = findViewById<Button>(R.id.button)

        // Chargement du dernier nom d'utilisateur enregistré
        val lastUsername = loadUsername()
        val url = loadUrl()

        editTextUsername.setText(lastUsername)

        buttonOK.setOnClickListener {
            val username = editTextUsername.text.toString()
            val password = editTextPassword.text.toString()
            saveUsername(username) // Enregistrement du nom d'utilisateur

            makeApiRequest(url, username, password)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun loadUsername(): String? {
        // Charger le nom d'utilisateur enregistré à partir des SharedPreferences
        return sharedPreferences.getString("username", null)
    }

    private fun saveUsername(username: String) {
        // Enregistrer le nom d'utilisateur dans les SharedPreferences
        val editor = sharedPreferences.edit()
        editor.putString("username", username)
        editor.apply()
    }

    private fun loadUrl(): String {
        return sharedPreferences.getString("url", "http://tomnab.fr/todo-api/") ?: "http://tomnab.fr/todo-api/"
    }

    private fun makeApiRequest(urlAPI: String, user: String, password: String) {



        if (isNetworkAvailable()) {
            // L'utilisateur dispose d'une connexion Internet, exécuter la requête Volley
            val requestQueue = Volley.newRequestQueue(this)

            val url = "$urlAPI" + "authenticate?user=$user&password=$password"

            val request = JsonObjectRequest(Request.Method.POST, url, null,
                { response ->
                    // Handle API response here
                    val token = response.getString("hash")
                    saveToken(token)


                    val userDao = db.userDao()
                    val userEntity = UserEntity(1, user, password)
                    GlobalScope.launch {
                        db.userDao().insertUser(userEntity)
                    }


                    val intent = Intent(this@MainActivity, ChoixListActivity::class.java)
                    intent.putExtra("username", user)
                    startActivity(intent)
                },
                { error -> Toast.makeText(this@MainActivity, "Error: $error", Toast.LENGTH_LONG).show()
                })

            requestQueue.add(request)

        } else {
            // L'utilisateur n'a pas de connexion Internet
            val userDao = db.userDao()
            GlobalScope.launch {
                val userEntity = userDao.getUserByUsername(user)


                if (userEntity != null && userEntity.password == password) {
                    // User exists in Room database and password matches
                    // Proceed with offline login
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Offline login successful", Toast.LENGTH_LONG).show()

                        val intent = Intent(this@MainActivity, ChoixListActivity::class.java)
                        intent.putExtra("username", user)
                        startActivity(intent)
                    }
                } else {
                    // User doesn't exist or password doesn't match
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Offline login failed", Toast.LENGTH_LONG).show()}
                }
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

    private fun saveToken(token: String) {
        val editor = sharedPreferences.edit()
        editor.putString("token", token)
        editor.apply()
    }

}

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "username") val username: String,
    @ColumnInfo(name = "password") val password: String
)


@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): UserEntity?
}

@Database(entities = [UserEntity::class, ListEntity::class, ItemEntity::class, PendingItemUpdate::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun listDao(): ListDao
    abstract fun itemDao(): ItemDao
    abstract fun pendingItemUpdateDao(): PendingItemUpdateDao
    companion object {
        @Volatile
        private var Instance: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            // if the Instance is not null, return it, otherwise create a new database instance.
            return Instance ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "PMR")
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { Instance = it }
            }
        }
    }
}

@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "user_id") val userId: String
)

@Dao
interface ListDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertList(list: ListEntity)

    @Query("SELECT * FROM lists WHERE user_id = :userId")
    suspend fun getAllListsByUser(userId: String): List<ListEntity>
}

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    val label: String,
    val url: String?,
    var checked: String,
    @ColumnInfo(name = "list_id") val listId: String // Foreign key column
)

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity)

    @Query("SELECT * FROM items WHERE list_id = :listId")
    suspend fun getAllItemsByList(listId: String): List<ItemEntity>

    @Query("SELECT * FROM items WHERE id = :itemId")
    suspend fun getItemById(itemId: String): ItemEntity

    @Update
    suspend fun updateItem(item: ItemEntity)
}

@Entity(tableName = "pending_item_updates")
data class PendingItemUpdate(
    @PrimaryKey val itemId: String,
    val checked: String
)
@Dao
interface PendingItemUpdateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePendingItemUpdate(update: PendingItemUpdate)

    @Query("DELETE FROM pending_item_updates WHERE itemId = :itemId")
    suspend fun deletePendingItemUpdate(itemId: String)

    @Query("SELECT * FROM pending_item_updates")
    suspend fun getAllPendingItemUpdates(): List<PendingItemUpdate>
}

