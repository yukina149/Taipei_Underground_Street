package com.example.cameraproject_2.model;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.example.cameraproject_2.model.FareEntryDao;
import com.example.cameraproject_2.model.FareEntry;

//資料庫改成版本二
@Database(entities = {FareEntry.class}, version = 2, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract FareEntryDao fareEntryDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "metro_fare_database")

                           // .fallbackToDestructiveMigration() 這是開發重建資料庫用的函數 穩定後用不到
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}