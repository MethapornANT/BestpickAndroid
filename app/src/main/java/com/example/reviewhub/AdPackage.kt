package com.bestpick.reviewhub.data // หรือ package ของคุณ

import com.google.gson.annotations.SerializedName // << อย่าลืมเพิ่ม import นี้ที่ด้านบนสุดของไฟล์

/**
 * Data class ที่ใช้รับข้อมูลแพ็กเกจโฆษณาจาก Server
 */
data class AdPackage(

    // ระบุชื่อตรงๆ จาก JSON (ถึงแม้ชื่อจะตรงกันแล้ว แต่ใส่ไว้จะชัดเจนกว่า)
    @SerializedName("id")
    val id: Int,

    @SerializedName("name")
    val name: String,

    // ✅ แก้ไขจุดที่ 2: ทำให้ description รับค่า null ได้ ป้องกันแอปแครช
    @SerializedName("description")
    val description: String?, // <-- เพิ่ม ? ตรงนี้

    // ✅ แก้ไขจุดที่ 1: ระบุให้ชัดเจนว่าให้ map มาจาก "duration_days" ใน JSON
    @SerializedName("duration_days")
    val durationDays: Int,

    @SerializedName("price")
    val price: Double
)