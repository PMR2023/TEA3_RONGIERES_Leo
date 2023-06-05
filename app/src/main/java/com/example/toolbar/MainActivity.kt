package com.example.toolbar

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var editTextUsername: EditText
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setSupportActionBar(findViewById(R.id.toolbar))

        // Initialisation des SharedPreferences
        sharedPreferences = getSharedPreferences("User", Context.MODE_PRIVATE)

        editTextUsername = findViewById(R.id.editTextText)
        val buttonOK = findViewById<Button>(R.id.button)

        // Chargement du dernier nom d'utilisateur enregistré
        val lastUsername = loadUsername()
        editTextUsername.setText(lastUsername)

        buttonOK.setOnClickListener {
            val username = editTextUsername.text.toString()
            saveUsername(username) // Enregistrement du nom d'utilisateur
            val intent = Intent(this@MainActivity, ChoixListActivity::class.java)
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
}
