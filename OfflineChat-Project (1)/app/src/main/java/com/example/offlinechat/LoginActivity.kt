package com.example.offlinechat

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        val name = findViewById<EditText>(R.id.name)
        val pass = findViewById<EditText>(R.id.pass)
        findViewById<Button>(R.id.login).setOnClickListener {
            val n = name.text.toString().trim().replace("|", " ")
            val p = pass.text.toString()
            if (n.isEmpty()) { name.error = "Apna naam likho"; return@setOnClickListener }
            if (p.length < 6) { pass.error = "Kam se kam 6 akshar"; return@setOnClickListener }
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra("name", n).putExtra("pass", p)
            )
        }
    }
}
