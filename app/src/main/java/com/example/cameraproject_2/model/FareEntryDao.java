package com.example.cameraproject_2.model;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.example.cameraproject_2.model.FareEntry;
import java.util.List;

@Dao
public interface FareEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE) // 如果已存在則替換
    void insertAll(List<FareEntry> fareEntries);

    @Query("SELECT * FROM fare_entries")
    List<FareEntry> getAllFareEntries(); // 這個方法需要在後台執行緒呼叫

    @Query("DELETE FROM fare_entries")
    void deleteAll(); // 這個方法需要在後台執行緒呼叫

}