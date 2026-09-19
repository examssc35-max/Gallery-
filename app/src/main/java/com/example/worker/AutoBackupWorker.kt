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
        if (isStopped) return Result.retry()
        return try {
            val db = AppDatabase.getDatabase(applicationContext)
            val prefs = PreferencesManager(applicationContext)
            val r2Client = R2Client()
            val r2Repo = R2RepositoryImpl(applicationContext, r2Client, prefs)
            val creds = r2Repo.getCredentials()
            val isConnected = creds.isVerified || (creds.secretAccessKey.isNotBlank() && creds.bucketName.isNotBlank() && creds.accountId.isNotBlank())
            if (!isConnected) {
                // If not connected, cannot perform background backup
                return Result.success()
            }

            val mediaDataSource = MediaStoreDataSource(applicationContext)
            val mediaRepo = MediaRepositoryImpl(mediaDataSource, db)
            val backupRepo = BackupRepositoryImpl(
                context = applicationContext,
                mediaRepository = mediaRepo,
                r2Repository = r2Repo,
                database = db,
                preferencesManager = prefs
            )

            backupRepo.runBackupPass(force = false)
            Result.success()
        } catch (e: Exception) {
            if (isStopped) {
                Result.retry()
            } else if (runAttemptCount < 3) {
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
