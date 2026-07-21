package dev.thinkami.podcastplayer.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FeedEntity::class, EpisodeEntity::class], version = 1, exportSchema = true)
abstract class PodcastDatabase : RoomDatabase() {

    abstract fun feedDao(): FeedDao

    abstract fun episodeDao(): EpisodeDao

    companion object {
        private const val DATABASE_NAME = "podcast.db"

        fun create(context: Context): PodcastDatabase =
            Room.databaseBuilder(context, PodcastDatabase::class.java, DATABASE_NAME)
                // 外部キーの CASCADE を効かせる(購読削除でエピソードも消えるようにするため)。
                .build()
    }
}
