package com.tokopedia.tokopatch.model

import android.support.annotation.Keep
import java.io.Serializable

@Keep
data class Key(
    val pKey: String = "",
    val iss: String = "",
    val target: String = "",
    val aud: String = ""
): Serializable {
    @Keep
    companion object {
        private val serialVersionUID = 8981401601075155259L
    }
}
