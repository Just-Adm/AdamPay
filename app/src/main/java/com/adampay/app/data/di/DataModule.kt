package com.adampay.app.data.di

import android.content.Context
import androidx.room.Room
import com.adampay.app.data.db.AdamDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AdamDatabase =
        Room.databaseBuilder(context, AdamDatabase::class.java, "adampay.db").build()

    /** Injected so date logic is testable. */
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()
}
