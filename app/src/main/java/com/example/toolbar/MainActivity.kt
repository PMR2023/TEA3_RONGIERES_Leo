package com.example.toolbar

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import com.android.volley.Request
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject



class MainActivity : AppCompatActivity() {

    private lateinit var editTextUsername: EditText
    private lateinit var editTextPassword: EditText
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))

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
            val intent = Intent(this@MainActivity, ChoixListActivity::class.java)

            makeApiRequest(url, username, password)

            intent.putExtra("username", username)
            startActivity(intent)
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
        val sharedPreferences = getSharedPreferences("User", Context.MODE_PRIVATE)
        return sharedPreferences.getString("url", "http://tomnab.fr/todo-api/") ?: "http://tomnab.fr/todo-api/"
    }

    private fun makeApiRequest(urlAPI: String, user: String, password: String) {
        val requestQueue = Volley.newRequestQueue(this)

        val url = "$urlAPI"+"authenticate?user=$user&password=$password"

        val request = JsonObjectRequest(Request.Method.POST, url, null,
            { response ->
                // Handle API response here
                val token = response.getString("hash")
                saveToken(token)
                Toast.makeText(this@MainActivity, "Received token: $token", Toast.LENGTH_LONG).show()
            },
            { error ->
                // Handle API request error
                Toast.makeText(this@MainActivity, "API connection error: ${error.message}", Toast.LENGTH_LONG).show()
            })

        requestQueue.add(request)
    }

    private fun saveToken(token: String) {
        val editor = sharedPreferences.edit()
        editor.putString("token", token)
        editor.apply()
    }

}
