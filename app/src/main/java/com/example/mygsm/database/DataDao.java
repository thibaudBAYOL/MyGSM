package com.example.mygsm.database;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
@Dao
public interface DataDao {
    @Insert
    void insert(Data data);

    @Update
    void update(Data data);

    @Query("SELECT * FROM Data ORDER BY alert DESC ,date DESC")
    List<Data> getAllData();

    @Query("SELECT * FROM Data WHERE alert=1 ORDER BY date DESC")
    List<Data> getAlertData();

    @Query("SELECT * FROM Data WHERE alert=0 ORDER BY date DESC")
    List<Data> getNotAlertData();

    @Query("SELECT * FROM Data WHERE Ci = :ci")
    Data getByCi(int ci);

}
