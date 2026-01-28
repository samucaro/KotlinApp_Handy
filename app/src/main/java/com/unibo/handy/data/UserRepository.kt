package com.unibo.handy.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class UserRepository(private val userDao: UserDAO) {
    suspend fun getOrCreateUser(): User {
        val existingUser = userDao.getLocalUser()

        return if (existingUser != null) {
            existingUser
        } else {
            val newUser = User()
            userDao.insertUser(newUser)
            newUser
        }
    }

}