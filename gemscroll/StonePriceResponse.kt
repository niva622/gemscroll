//StonePriceResponse.kt
package com.example.gemscroll

data class StonePriceResponse(
    val data: List<StoneData>
)

data class StoneData(
    val sellPrice: Double
)