package com.lukehemmin.lukeVanilla.System.Database

class DatabaseInitializer(private val database: Database) {

    fun createTables() {
        createSecretKeyTable()
        createPlayerDataTable()
        createJoinQuitMessageTable()
        createSettingsTable()
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

    private fun createPlayerDataTable() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS Player_Data (" +
                        "UUID VARCHAR(36) NOT NULL, " +
                        "NickName VARCHAR(30) NOT NULL, " +
                        "NameTag VARCHAR(255), " +
                        "DiscordID VARCHAR(30), " +
                        "IsAuth TINYINT(1) NOT NULL DEFAULT 0, " +
                        "AuthCode VARCHAR(6), " +
                        "First_Join TINYINT(1) NOT NULL DEFAULT 1, " +
                        "PRIMARY KEY (UUID)" +
                        ")"
            )
        }
    }

    private fun createJoinQuitMessageTable() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS Join_Quit_Message (" +
                        "service_type VARCHAR(30) NOT NULL, " +
                        "message_type VARCHAR(30) NOT NULL, " +
                        "message TEXT NOT NULL, " +
                        "PRIMARY KEY (service_type, message_type)" +
                        ")"
            )
        }
    }

    private fun createSettingsTable() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS Settings (" +
                        "setting_type VARCHAR(50) NOT NULL, " +
                        "setting_value TEXT NOT NULL, " +
                        "PRIMARY KEY (setting_type)" +
                        ")"
            )
        }
    }
}