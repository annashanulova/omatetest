package com.omatetest.makora.omatetest.utils;

import android.content.Context;
import android.util.Log;

import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

/**
 * Created by anna.hanulova on 02.04.2015.
 */
public class DBHelper extends SQLiteOpenHelper {
    private static DBHelper mInstance = null;

    private static final int DATABASE_VERSION = 1;
    private Context mContext;
    private static final String TAG = "DBHelper";
    private static final String DATABASE_NAME = "omatetestDB";

    private static final String INI_CREATE_COMMANDS[] = {
            "CREATE TABLE IF NOT EXISTS `users_acc_raw` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "`start` long NOT NULL," +
                    "`x` double NOT NULL," +
                    "`y` double NOT NULL, " +
                    "`z` double NOT NULL, " +
                    "`uploaded` BOOLEAN DEFAULT 0)",
            "CREATE TABLE IF NOT EXISTS `users_wifi_bssids` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "`start` long NOT NULL," +
                    "`bssid` char(65) NOT NULL," +
                    "`level` int NOT NULL, " +
                    "`uploaded` BOOLEAN DEFAULT 0)"
    };

    public static DBHelper getInstance(Context context) {

        if (mInstance == null) {
            Log.i(TAG, "Creating DBHelper start.., context: " + context);
            // mInstance = new MossDBHelper(ctx.getApplicationContext());
            mInstance = new DBHelper(context);
        }
        return mInstance;
    }

    /**
     * constructor should be private to prevent direct instantiation. make call
     * to static factory method "getInstance()" instead.
     */
    private DBHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.mContext = context;

        try {
            SQLiteDatabase.loadLibs(context);
        } catch (UnsatisfiedLinkError e) {
            Log.e("Cipher", "Libs failed to load" + e);
        }
    }

    public static SQLiteDatabase getReadableInstance(Context context) {
        DBHelper dbHelper = DBHelper.getInstance(context);
        SQLiteDatabase db = dbHelper.getReadableDatabase("\105\156\164er");
        return db;
    }

    public static SQLiteDatabase getWritableInstance(Context context) {
        DBHelper dbHelper = DBHelper.getInstance(context);
        SQLiteDatabase db = dbHelper.getReadableDatabase("\105\156\164er");
        return db;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        for (String init_command : INI_CREATE_COMMANDS) {
            try {
                db.execSQL(init_command);
                Log.i("DBHelper", init_command + " successful");
            } catch (SQLException e) {
                Log.e("SQL", e + " " + init_command);
            }
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int i, int i2) {
        Log.d(TAG,"onUpgrade called");
    }
}
