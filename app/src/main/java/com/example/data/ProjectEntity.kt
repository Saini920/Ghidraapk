package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val fileType: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sourceCode: String? = null,
    val status: String = "PENDING" // PENDING, DECOMPILING, COMPLETED, FAILED
)
