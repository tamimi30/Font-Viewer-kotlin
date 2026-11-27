package com.oneui.fontviewer.ui.home

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem

import com.oneui.fontviewer.R
import com.oneui.fontviewer.ui.base.BaseActivity

class HomeActivity : BaseActivity() {

    private var mHomeFragment: HomeFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        if (savedInstanceState == null) {
            val fragment = HomeFragment()
            mHomeFragment = fragment
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.home_container, fragment)
                .commit()
        } else {
            mHomeFragment = supportFragmentManager
                .findFragmentById(R.id.home_container) as HomeFragment?
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val homeFragment = mHomeFragment
        if (homeFragment != null && homeFragment.handleBackPressed()) {
            return
        }

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O && isTaskRoot) {
            finishAfterTransition()
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_home, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_app_info) {
            val intent = Intent(
                "android.settings.APPLICATION_DETAILS_SETTINGS",
                Uri.fromParts("package", packageName, null)
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
