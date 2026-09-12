package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.cloudflare.R2Client
import com.example.data.local.AppDatabase
import com.example.data.local.MediaStoreDataSource
import com.example.data.local.PreferencesManager
import com.example.data.repository.BackupRepositoryImpl
import com.example.data.repository.MediaRepositoryImpl
import com.example.data.repository.R2RepositoryImpl

class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val prefs = PreferencesManager(applicationContext)
            val r2Client = R2Client()
            val r2Repo = R2RepositoryImpl(applicationContext, r2Client, prefs)
            val mediaDataSource = MediaStoreDataSource(applicationContext)
            val mediaRepo = MediaRepositoryImpl(mediaDataSource, db)
            val backupRepo = BackupRepositoryImpl(
                context = applicationContext,
                mediaRepository = mediaRepo,
                r2Repository = r2Repo,
                database = db,
                preferencesManager = prefs
            )

            backupRepo.runBackupPass()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "cloudgallery_auto_backup_periodic"
        const val WORK_NAME_ONETIME = "cloudgallery_auto_backup_onetime"
    }
}
