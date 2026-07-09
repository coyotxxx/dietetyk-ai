package pl.filebit.dietetyk.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import pl.filebit.dietetyk.Backup
import pl.filebit.dietetyk.DietetykApp

/**
 * Codzienna automatyczna kopia zapasowa (lokalna). Zero utraty danych — zgodne z żelazną zasadą.
 * Zapisuje snapshot (db+prefs) do zewn. katalogu aplikacji; timestamp trafia do „Ostatnia kopia".
 */
class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as DietetykApp
        if (!app.settings.autoBackupEnabled) return Result.success()
        // Nie rób kopii pustej bazy (worker potrafi odpalić przy 1. starcie, zanim powstanie profil) —
        // inaczej „ostatnia kopia" nadpisuje się pustką i restore = utrata danych.
        if (app.profileRepo.get() == null) return Result.success()
        Backup.writeLocalBackup(app, app)
        return Result.success()
    }
}
