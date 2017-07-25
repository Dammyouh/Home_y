package com.example.yangxiaoyu.home_y;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by yangxiaoyu on 17-7-20.
 */

public class MyDatabaseHelper extends SQLiteOpenHelper {
    public  static  final String CREAT_ACTIVITYTABLE = "create table Activity(id interger primary key autoincrement," +
            "Activity text," +
            "Status text,"+
            "ThisTime integer," +
            "TotalTime integer," +
            "WaitTime integer)";

    private  Context mcontext;
    public MyDatabaseHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        super(context, name, factory, version);
        mcontext = context;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREAT_ACTIVITYTABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
