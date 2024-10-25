package com.lukehemmin.lukeVanilla.System.Database

class DatabaseInitializer(private val database: Database) {

    fun createTables() {
        createSecretKeyTable()
        // 다른 테이블 생성 코드 추가 가능
    }

    private fun createSecretKeyTable() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS Secret_Key (" +
                        "Code VARCHAR(30) NOT NULL" +
                        ")"
            )
        }
    }
}