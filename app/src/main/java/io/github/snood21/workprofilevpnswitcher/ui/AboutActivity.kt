package io.github.snood21.workprofilevpnswitcher.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import io.github.snood21.workprofilevpnswitcher.BuildConfig
import io.github.snood21.workprofilevpnswitcher.R
import io.github.snood21.workprofilevpnswitcher.util.LanguageUtils
import androidx.core.net.toUri

class AboutActivity : AppCompatActivity() {

    companion object {
        private const val GITHUB_URL = "https://github.com/snood21/workprofile-vpn-switcher"
        private const val BOOSTY_URL = "https://boosty.to/snood21/donate"
        private const val DONATTY_URL = "https://donatty.com/snood21"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageUtils.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.action_about)

        findViewById<TextView>(R.id.tv_version).text =
            getString(R.string.about_version, BuildConfig.VERSION_NAME)

        findViewById<TextView>(R.id.tv_developer).text =
            getString(R.string.about_developer)

        findViewById<TextView>(R.id.tv_license).text =
            getString(R.string.about_license)

        findViewById<TextView>(R.id.tv_github).setOnClickListener {
            openUrl(GITHUB_URL)
        }
        findViewById<TextView>(R.id.tv_boosty).setOnClickListener {
            openUrl(BOOSTY_URL)
        }
        findViewById<TextView>(R.id.tv_donatty).setOnClickListener {
            openUrl(DONATTY_URL)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    }
}
