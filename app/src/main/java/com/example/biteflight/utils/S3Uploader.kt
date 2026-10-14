package com.example.biteflight.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.regions.Region
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.PutObjectRequest
import com.example.biteflight.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object S3Uploader {

    private const val TAG = "S3Uploader"

    fun uploadImage(
        context: Context,
        imageUri: Uri,
        folderName: String = "uploads",
        onResult: (Resource<String>) -> Unit
    ) {
        onResult(Resource.Loading)

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val accessKey = BuildConfig.AWS_ACCESS_KEY.trim()
                val secretKey = BuildConfig.AWS_SECRET_KEY.trim()
                val bucketName = BuildConfig.AWS_BUCKET_NAME.trim()
                val regionString = BuildConfig.AWS_REGION.trim().ifEmpty { "eu-north-1" }

                Log.d(TAG, "Initiating S3 upload: bucket='$bucketName', region='$regionString'")

                if (accessKey.isEmpty() || secretKey.isEmpty() || bucketName.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        onResult(Resource.Error("AWS credentials or Bucket Name not configured in local.properties."))
                    }
                    return@launch
                }

                // 1. Copy Uri to temporary local file
                val file = uriToFile(context, imageUri)
                if (file == null || !file.exists()) {
                    withContext(Dispatchers.Main) {
                        onResult(Resource.Error("Failed to process image file."))
                    }
                    return@launch
                }

                // 2. Initialize AWS S3 Client with regional endpoint for eu-north-1
                val awsCredentials = BasicAWSCredentials(accessKey, secretKey)
                val s3Client = AmazonS3Client(awsCredentials)

                val regionEnum = try {
                    Regions.fromName(regionString)
                } catch (e: Exception) {
                    Regions.EU_NORTH_1
                }
                s3Client.setRegion(Region.getRegion(regionEnum))
                s3Client.setEndpoint("s3.$regionString.amazonaws.com")

                // 3. Define S3 Object Key
                val fileName = "${UUID.randomUUID()}.jpg"
                val objectKey = "$folderName/$fileName"

                // 4. Upload file to S3
                val putObjectRequest = PutObjectRequest(bucketName, objectKey, file)
                s3Client.putObject(putObjectRequest)

                // 5. Construct Public S3 URL
                val publicUrl = "https://$bucketName.s3.$regionString.amazonaws.com/$objectKey"
                Log.d(TAG, "Uploaded successfully to: $publicUrl")

                // Clean up temp file
                file.delete()

                withContext(Dispatchers.Main) {
                    onResult(Resource.Success(publicUrl))
                }

            } catch (e: Exception) {
                Log.e(TAG, "S3 Upload failed", e)
                withContext(Dispatchers.Main) {
                    onResult(Resource.Error(e.localizedMessage ?: "AWS S3 upload failed."))
                }
            }
        }
    }

    fun deleteImage(
        oldImageUrl: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (oldImageUrl.isBlank() || !oldImageUrl.startsWith("http")) {
            onComplete?.invoke(false)
            return
        }

        GlobalScope.launch(Dispatchers.IO) {
            try {
                val accessKey = BuildConfig.AWS_ACCESS_KEY.trim()
                val secretKey = BuildConfig.AWS_SECRET_KEY.trim()
                val bucketName = BuildConfig.AWS_BUCKET_NAME.trim()
                val regionString = BuildConfig.AWS_REGION.trim().ifEmpty { "eu-north-1" }

                val uri = Uri.parse(oldImageUrl)
                val objectKey = uri.path?.trimStart('/')

                if (accessKey.isNotEmpty() && secretKey.isNotEmpty() && bucketName.isNotEmpty() && !objectKey.isNullOrEmpty()) {
                    val awsCredentials = BasicAWSCredentials(accessKey, secretKey)
                    val s3Client = AmazonS3Client(awsCredentials)

                    val regionEnum = try {
                        Regions.fromName(regionString)
                    } catch (e: Exception) {
                        Regions.EU_NORTH_1
                    }
                    s3Client.setRegion(Region.getRegion(regionEnum))
                    s3Client.setEndpoint("s3.$regionString.amazonaws.com")

                    Log.d(TAG, "Deleting old S3 object: bucket='$bucketName', key='$objectKey'")
                    s3Client.deleteObject(bucketName, objectKey)
                    Log.d(TAG, "Successfully deleted old S3 object: $objectKey")
                    withContext(Dispatchers.Main) { onComplete?.invoke(true) }
                } else {
                    withContext(Dispatchers.Main) { onComplete?.invoke(false) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete old S3 image: $oldImageUrl", e)
                withContext(Dispatchers.Main) { onComplete?.invoke(false) }
            }
        }
    }

    private fun uriToFile(context: Context, uri: Uri): File? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val tempFile = File.createTempFile("upload_", ".jpg", context.cacheDir)
            val outputStream = FileOutputStream(tempFile)
            inputStream?.use { input ->
                outputStream.use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error converting URI to file", e)
            null
        }
    }
}
