package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.BalanceDao
import com.example.data.dao.ExchangeRateDao
import com.example.data.dao.TradeDao
import com.example.data.model.Balance
import com.example.data.model.ExchangeRate
import com.example.data.model.Trade

@Database(entities = [Trade::class, ExchangeRate::class, Balance::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tradeDao(): TradeDao
    abstract fun exchangeRateDao(): ExchangeRateDao
    abstract fun balanceDao(): BalanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add columns to trades
                db.execSQL("ALTER TABLE trades ADD COLUMN cryptoCurrency TEXT NOT NULL DEFAULT 'USDT'")
                db.execSQL("ALTER TABLE trades ADD COLUMN fiatCurrency TEXT NOT NULL DEFAULT 'SYP'")
                db.execSQL("ALTER TABLE trades ADD COLUMN customerName TEXT NOT NULL DEFAULT ''")

                // Add columns to exchange_rates
                db.execSQL("ALTER TABLE exchange_rates ADD COLUMN cryptoCurrency TEXT NOT NULL DEFAULT 'USDT'")
                db.execSQL("ALTER TABLE exchange_rates ADD COLUMN fiatCurrency TEXT NOT NULL DEFAULT 'SYP'")

                // Add columns to balances
                db.execSQL("ALTER TABLE balances ADD COLUMN balanceUSD REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE balances ADD COLUMN balanceTRY REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE balances ADD COLUMN balanceEUR REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE balances ADD COLUMN balanceUSDC REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE balances ADD COLUMN balanceBTC REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE balances ADD COLUMN balanceETH REAL NOT NULL DEFAULT 0.0")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "p2p_trader_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
