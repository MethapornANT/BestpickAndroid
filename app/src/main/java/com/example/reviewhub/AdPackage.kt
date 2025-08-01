package com.bestpick.reviewhub.data // <<<< สำคัญ: ตรวจสอบว่าแพ็กเกจนี้ตรงกับตำแหน่งจริงของไฟล์ AdPackage.kt ในโปรเจกต์ของคุณ

data class AdPackage(
    val id: Int,
    val name: String,
    val description: String, // ตรวจสอบชื่อ field นี้จาก JSON response ของคุณ (บางทีอาจเป็น "short_description" หรือ "details")
    val durationDays: Int,   // ตรวจสอบชื่อ field นี้จาก JSON response ของคุณ (บางทีอาจเป็น "package_duration")
    val price: Double        // ตรวจสอบชื่อ field นี้จาก JSON response ของคุณ
)