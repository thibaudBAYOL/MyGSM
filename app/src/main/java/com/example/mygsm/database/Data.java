package com.example.mygsm.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class Data {
    @PrimaryKey(autoGenerate = false)
    public int Ci;

    public String name;


    public String info;
    public Boolean alert;
    public int count;

    public long date; // timestamp en millisecondes

    public Data(String name,String info, int Ci,Boolean alert, int count, long date) {
        this.name = name;
        this.Ci = Ci;
        this.alert = alert;
        this.info = info;
        this.count = count;
        this.date = date;
    }
}


