package com.example.meshchat

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import com.example.meshchat.data.UserPreferences
import com.example.meshchat.databinding.ActivityLoginBinding
import com.example.meshchat.ui.DeviceListActivity

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val userPrefs = UserPreferences(this)
        // FIX: If the user's name is already saved, skip the login screen.
        if (userPrefs.getUserName().isNotEmpty()) {
            goToDeviceList()
            return // Finish activity to prevent login screen from appearing
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Animation
        val animation = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in)
        binding.textWelcome.startAnimation(animation)
        binding.textPromptName.startAnimation(animation)
        binding.nameInputLayout.startAnimation(animation)
        binding.saveButton.startAnimation(animation)

        binding.saveButton.setOnClickListener {
            val name = binding.nameInput.text.toString().trim()
            if (name.isNotEmpty()) {
                userPrefs.saveUserName(name)
                goToDeviceList()
            } else {
                binding.nameInput.error = "Name cannot be empty"
            }
        }
    }

    private fun goToDeviceList() {
        val intent = Intent(this, DeviceListActivity::class.java)
        startActivity(intent)
        finish()
    }
}
