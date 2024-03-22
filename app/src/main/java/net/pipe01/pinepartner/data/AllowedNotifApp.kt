package net.pipe01.pinepartner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class AllowedNotifApp(
    @PrimaryKey val packageName: String,
)
