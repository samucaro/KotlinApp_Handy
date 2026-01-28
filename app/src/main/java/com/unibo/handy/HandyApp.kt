package com.unibo.handy

import android.app.Application
import com.unibo.handy.data.HandyDB
import com.unibo.handy.data.UserRepository

class HandyApp : Application() {
    val db by lazy { HandyDB.getDatabase(this) }
    val repository by lazy { UserRepository(db.userDao()) }
}