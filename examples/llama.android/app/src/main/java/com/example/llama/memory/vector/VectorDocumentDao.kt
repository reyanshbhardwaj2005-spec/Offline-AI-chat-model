package com.example.llama.memory.vector

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query

@Dao
interface VectorDocumentDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: VectorDocumentEntity): Long

    @Query("SELECT * FROM vector_documents")
    suspend fun getAll(): List<VectorDocumentEntity>

    @Query("SELECT * FROM vector_documents WHERE sourceType = :sourceType AND sourceId = :sourceId LIMIT 1")
    suspend fun get(sourceType: String, sourceId: Long): VectorDocumentEntity?

    @Query("SELECT COUNT(*) FROM vector_documents")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM vector_documents WHERE sourceType = :sourceType")
    suspend fun countBySourceType(sourceType: String): Int

    @Query("SELECT COUNT(*) FROM vector_documents WHERE model = :model")
    suspend fun countByModel(model: String): Int

    @Query("DELETE FROM vector_documents WHERE sourceType = :sourceType AND sourceId = :sourceId")
    suspend fun delete(sourceType: String, sourceId: Long)

    @Query("DELETE FROM vector_documents WHERE sourceType = :sourceType AND sourceId IN (:sourceIds)")
    suspend fun deleteMany(sourceType: String, sourceIds: List<Long>)

    @Query("DELETE FROM vector_documents WHERE conversationId = :conversationId")
    suspend fun deleteForConversation(conversationId: Long)

    @Query("DELETE FROM vector_documents")
    suspend fun deleteAll()
}
