package io.legado.server.model

/**
 * Standard API response format matching Legado's web API
 */
data class ReturnData<T>(
    val isSuccess: Boolean = true,
    val errorMsg: String = "",
    val data: T? = null
) {
    companion object {
        fun <T> success(data: T): ReturnData<T> = ReturnData(true, "", data)
        fun error(msg: String): ReturnData<Nothing> = ReturnData(false, msg, null)
    }
}
