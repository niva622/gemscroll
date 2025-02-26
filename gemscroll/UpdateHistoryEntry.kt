// UpdateHistoryEntry.kt
package com.example.gemscroll

data class UpdateHistoryEntry(
    val timestamp: String, // Формат: "dd.MM.yyyy HH:mm:ss"
    val status: String,    // "Успешно" или "Неуспешно"
    val message: String?   // Дополнительная информация или сообщение об ошибке
)
