package com.digimenu.core.di

import com.digimenu.core.firebase.FirebaseRefs
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the Firebase primitives used across both apps. */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideDatabase(): FirebaseDatabase {
        val db = FirebaseDatabase.getInstance(FirebaseRefs.DATABASE_URL)
        runCatching { db.setPersistenceEnabled(true) }
        return db
    }

    @Provides
    @Singleton
    fun provideAuth(): FirebaseAuth = FirebaseAuth.getInstance()
}
