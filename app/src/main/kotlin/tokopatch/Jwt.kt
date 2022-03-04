package com.meituan.test

import android.content.Context
import android.util.Base64
import android.util.Log
import com.tokopedia.tokopatch.model.Key
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import java.io.*
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class Jwt {

    fun readFile(context: Context, path: String): Key{
        try {
            val fileIn = context.resources.openRawResource(R.raw.robust)
//            val fileIn = FileInputStream(path)
            val gzipIn = GZIPInputStream(fileIn)
            val byteOut = ByteArrayOutputStream()
            var count: Int
            val data = ByteArray(1024)
            while (gzipIn.read(data, 0, 1024).also { count = it } != -1) {
                byteOut.write(data, 0, count)
            }
            val byteIn = ByteArrayInputStream(byteOut.toByteArray())
            val oi = ObjectInputStream(byteIn)
            val key : Key = oi.readObject() as Key
            println(key.toString())
            fileIn.close()
            gzipIn.close()
            oi.close()
            return key
        }catch (e: Exception){
            e.printStackTrace()
        }
        return Key()
    }

    fun writeFile(): String {
        val key = Key(
            "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC1KKXVLquV1ZsFrpSt9aF2jVYX2JoEt7IbxamhQFISYTjIUWOKmAZh5rv32urjKIkX7EGeMgfYFZh690g9T5iuBLJfDX5Cc5wnpRGPwnpNkd77vmBTpcUEFKK7qXWXXP0453ZFofqM9iOsYJnK5Bb0QjmtP8MDUIItnopA9uygQMRVZlCMqsC5q7S00Pjfo9d4HyWAF1v6rk3laH/BV9xx1wXjmIfpWWHlGLvmI3oblFkA5tky8eWPxMYzvHlWqUGck4bT9ekwe6dI1e7SLabvrY/TFfKwhIQe+hoUIHCQCVAUiAFLcep9rLFeyNaSASUT96Y/NjfrvlPY3/imbzj5AgMBAAECggEAQIvDjlHrG984NU2PNu4iQeG4z5VCxzSGNsP2HPiUZW0TdLge/dYNzBsQVQ7AiwA53Ha2qH1N8y06n3F04Td3gGU3AowFtPqJJEz5lV2nAvVa3BpoKtWQ/VO26aNGvRAKPxilOMkuQsQ+UKA4m6QqE4x3DWX+/zLTtxpaSyxPggcIDZomKuXMYAH81VwGlFliR2plisyj/wG6NSovZoRCqQcoG46vA/87vYcyZ5m5JIUdFf3gWGzV6LQFz6w3FjINQcG8oy7SAMDvkbvIsgXd7w7r3J0pLQ9PmVL9gVZs5Is2M+xQWhrrOYyMtuUOUOZ80cztbzEz3aN4G44HditN6wKBgQDcMKdVK/ZbgT8rp63sFvH+FZ8b82XN2ycEYRPt6z4HIpMqxrcJUGeZXjxYJR1vJKX7FQ8kR1QZLDd5YMeEh0a6KzslVmPfJx2RsHwZh55zMxAnb0E4XXTwIoE9rBjd0L/NQJgNim/XhuRP1bOAZQRrDOJ8pV0cCDf9c9v7mViGcwKBgQDSnvluHqrPd+5FpQ7KUeg3DtTRRFr1MvbMdYJRrpe5PYGk2OkH8ciJWmCzpXA5zvB/gwiEdBpRDPVlpgBVWr0ZEjon7wlHugPvP7gebhGsDK6Q+deoSCWfp72kOJS2iZZjkHrEaj8etMOLE4aV+j1uFjcCYjq8r0ZyHuR1Kd674wKBgDVzZhtp2Zrq7A/H25N1GndofkBFvI/VREpu2myl71/CB+GZbXNIXm2/j3yCPfvt9JDX0t4mpoaZ0jmXwbctM4Eb33a32vSfxTDJm8aCwncKjUBVZIqvPSTR63eyIDMwam8D4CVhVrcGGsQ6hyGC5Cisbwp9BfY5FIZlKqCP4Ap7AoGBAI6j24kYe9XGAzhncHzUu8+N29Nd17v3p+0QKBHpjBeH8CUUQb1/obBj5NnFURvVakrxEvOhLbF2dTtCETe9HBO+pGQnHsHU2JVPMgJpyM4cSJ0ml0cAlXpqv6RYLV7yD0eesYYT7mt9QHEP6DXqI3BK2zZiECV5Dtx6z34JvS7BAoGBAKIrJsPgaMIUlrl5m6D6scCPHNCGKnQRAu0ma9Eol9+Aor9CQ0YxMLhCigjXv94Tveiv2tsnvnJDpcWEzTp7sksDjjtskXVIczWQWG8UfPbNtwzgDn+lDaEoEDc14ACP/2Y0gCGLNsjOoLIdeDyhoGZDoVyLdRYLkLofZMN3ST01",
            "android-poc-269407@appspot.gserviceaccount.com",
            "https://asia-southeast2-android-poc-269407.cloudfunctions.net/robust",
            "https://www.googleapis.com/oauth2/v4/token"
        )
        try {
            val tempFile = File.createTempFile("key", ".key")
            val fileOut = FileOutputStream(tempFile)
            val byteOut = ByteArrayOutputStream()
            val objOut = ObjectOutputStream(byteOut)
            objOut.writeObject(key)
            val gzip = GZIPOutputStream(fileOut)
            gzip.write(byteOut.toByteArray())
            objOut.close();
            gzip.flush();
            gzip.close();
            fileOut.flush()
            fileOut.close()
            Log.d("KEY_FILE", tempFile.absolutePath)
            return tempFile.absolutePath
        } catch (e: Exception){
            e.printStackTrace()
        }
        return ""
    }

    fun token(key: Key): String{

//        val key = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQC1KKXVLquV1ZsFrpSt9aF2jVYX2JoEt7IbxamhQFISYTjIUWOKmAZh5rv32urjKIkX7EGeMgfYFZh690g9T5iuBLJfDX5Cc5wnpRGPwnpNkd77vmBTpcUEFKK7qXWXXP0453ZFofqM9iOsYJnK5Bb0QjmtP8MDUIItnopA9uygQMRVZlCMqsC5q7S00Pjfo9d4HyWAF1v6rk3laH/BV9xx1wXjmIfpWWHlGLvmI3oblFkA5tky8eWPxMYzvHlWqUGck4bT9ekwe6dI1e7SLabvrY/TFfKwhIQe+hoUIHCQCVAUiAFLcep9rLFeyNaSASUT96Y/NjfrvlPY3/imbzj5AgMBAAECggEAQIvDjlHrG984NU2PNu4iQeG4z5VCxzSGNsP2HPiUZW0TdLge/dYNzBsQVQ7AiwA53Ha2qH1N8y06n3F04Td3gGU3AowFtPqJJEz5lV2nAvVa3BpoKtWQ/VO26aNGvRAKPxilOMkuQsQ+UKA4m6QqE4x3DWX+/zLTtxpaSyxPggcIDZomKuXMYAH81VwGlFliR2plisyj/wG6NSovZoRCqQcoG46vA/87vYcyZ5m5JIUdFf3gWGzV6LQFz6w3FjINQcG8oy7SAMDvkbvIsgXd7w7r3J0pLQ9PmVL9gVZs5Is2M+xQWhrrOYyMtuUOUOZ80cztbzEz3aN4G44HditN6wKBgQDcMKdVK/ZbgT8rp63sFvH+FZ8b82XN2ycEYRPt6z4HIpMqxrcJUGeZXjxYJR1vJKX7FQ8kR1QZLDd5YMeEh0a6KzslVmPfJx2RsHwZh55zMxAnb0E4XXTwIoE9rBjd0L/NQJgNim/XhuRP1bOAZQRrDOJ8pV0cCDf9c9v7mViGcwKBgQDSnvluHqrPd+5FpQ7KUeg3DtTRRFr1MvbMdYJRrpe5PYGk2OkH8ciJWmCzpXA5zvB/gwiEdBpRDPVlpgBVWr0ZEjon7wlHugPvP7gebhGsDK6Q+deoSCWfp72kOJS2iZZjkHrEaj8etMOLE4aV+j1uFjcCYjq8r0ZyHuR1Kd674wKBgDVzZhtp2Zrq7A/H25N1GndofkBFvI/VREpu2myl71/CB+GZbXNIXm2/j3yCPfvt9JDX0t4mpoaZ0jmXwbctM4Eb33a32vSfxTDJm8aCwncKjUBVZIqvPSTR63eyIDMwam8D4CVhVrcGGsQ6hyGC5Cisbwp9BfY5FIZlKqCP4Ap7AoGBAI6j24kYe9XGAzhncHzUu8+N29Nd17v3p+0QKBHpjBeH8CUUQb1/obBj5NnFURvVakrxEvOhLbF2dTtCETe9HBO+pGQnHsHU2JVPMgJpyM4cSJ0ml0cAlXpqv6RYLV7yD0eesYYT7mt9QHEP6DXqI3BK2zZiECV5Dtx6z34JvS7BAoGBAKIrJsPgaMIUlrl5m6D6scCPHNCGKnQRAu0ma9Eol9+Aor9CQ0YxMLhCigjXv94Tveiv2tsnvnJDpcWEzTp7sksDjjtskXVIczWQWG8UfPbNtwzgDn+lDaEoEDc14ACP/2Y0gCGLNsjOoLIdeDyhoGZDoVyLdRYLkLofZMN3ST01"
        val encodedKey: ByteArray = Base64.decode(key.pKey, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(encodedKey)

        val kf: KeyFactory = KeyFactory.getInstance("RSA")
        val privKey = kf.generatePrivate(keySpec)
        val now = Date()
        val exp = Date().add(10)
        val jwt = Jwts.builder()
            .claim("iss", key.iss)
            .claim("target_audience", key.target)
            .claim("aud", key.aud)
            .setIssuedAt(now)
            .setExpiration(exp)
            .signWith(privKey, SignatureAlgorithm.RS256)
            .compact()
        return jwt
    }

    private fun Date.add(minutes: Int): Date {
        val minuteMillis: Long = 60000 //millisecs
        val curTimeInMs: Long = this.time
        val result = Date(curTimeInMs + minutes * minuteMillis)
        this.time = result.time
        return this
    }

}