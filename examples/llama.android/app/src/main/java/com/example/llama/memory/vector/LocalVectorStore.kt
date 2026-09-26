package com.example.llama.memory.vector

class LocalVectorStore(
    private val dao: VectorDocumentDao
) : VectorStore {

    override suspend fun upsert(
        sourceType: String,
        sourceId: Long,
        conversationId: Long?,
        content: String,
        vector: FloatArray,
        model: String
    ) {
        dao.upsert(
            VectorDocumentEntity(
                sourceType = sourceType,
                sourceId = sourceId,
                conversationId = conversationId,
                content = content,
                vector = VectorSerializer.toBytes(vector),
                dimension = vector.size,
                model = model
            )
        )
    }

    override suspend fun search(
        queryVector: FloatArray,
        topK: Int,
        sourceTypes: Set<String>?,
        conversationId: Long?,
        minimumScore: Float
    ): List<VectorSearchResult> {
        return dao.getAll()
            .asSequence()
            .filter { sourceTypes == null || it.sourceType in sourceTypes }
            .filter { conversationId == null || it.conversationId == conversationId }
            .mapNotNull { document ->
                val vector = VectorSerializer.fromBytes(document.vector)
                if (vector.size != queryVector.size) return@mapNotNull null

                val score = VectorMath.cosineSimilarity(queryVector, vector)
                if (score < minimumScore) return@mapNotNull null

                VectorSearchResult(
                    sourceType = document.sourceType,
                    sourceId = document.sourceId,
                    conversationId = document.conversationId,
                    content = document.content,
                    score = score
                )
            }
            .sortedByDescending { it.score }
            .take(topK)
            .toList()
    }

    override suspend fun delete(sourceType: String, sourceId: Long) {
        dao.delete(sourceType, sourceId)
    }

    override suspend fun deleteMany(sourceType: String, sourceIds: List<Long>) {
        if (sourceIds.isNotEmpty()) dao.deleteMany(sourceType, sourceIds)
    }

    override suspend fun deleteForConversation(conversationId: Long) {
        dao.deleteForConversation(conversationId)
    }

    override suspend fun clear() {
        dao.deleteAll()
    }
}
