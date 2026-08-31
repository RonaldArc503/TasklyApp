package com.ronaldcolocho.taskly.domain.util

fun normalizePhone(raw: String): String {
    val digits = raw.replace(Regex("\\D"), "")
    return if (digits.startsWith("503") && digits.length > 8) digits.drop(3) else digits
}

fun formatPhone(raw: String): String {
    val num = normalizePhone(raw)
    if (num.isEmpty()) return ""
    if (num.length == 8) return "${num.take(4)} ${num.drop(4)}"
    if (num.length <= 6) return num
    return "+${num.take(2)} ${num.drop(2).take(3)} ${num.drop(5).take(3)} ${num.drop(8)}".trim()
}

fun phoneTakenError(): Exception =
    Exception("El teléfono ya está registrado por otro usuario.")
