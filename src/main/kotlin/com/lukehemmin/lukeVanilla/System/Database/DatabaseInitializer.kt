package com.lukehemmin.lukeVanilla.System.Database

class DatabaseInitializer(private val database: Database) {

    fun createTables() {
        createSecretKeyTable()
        createPlayerDataTable()
        createPlayerAuthTable()
        createPlayerNameTagTable()
        createJoinQuitMessageTable()
        createSettingsTable()
        createHalloweenItemOwnerTable()
        // 다른 테이블 생성 코드 추가 가능
    }

    private fun createSecretKeyTable() { // 비밀키 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS Secret_Key (" +
                        "Code VARCHAR(30) NOT NULL" +
                        ")"


            )
        }
    }

    private fun createPlayerDataTable() { // 플레이어 데이터 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS Player_Data (" +
                        "UUID VARCHAR(36) NOT NULL, " +
                        "NickName VARCHAR(30) NOT NULL, " +
                        "DiscordID VARCHAR(30), " +
                        "PRIMARY KEY (UUID)" +
                        ")"
            )
        }
    }

    private fun createPlayerAuthTable() { // 플레이어 인증 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS Player_Auth (" +
                        "UUID VARCHAR(36) NOT NULL, " +
                        "IsAuth TINYINT(1) NOT NULL DEFAULT 0, " +
                        "AuthCode VARCHAR(6), " +
                        "IsFirst TINYINT(1) NOT NULL DEFAULT 1, " +
                        "PRIMARY KEY (UUID)" +
                        ")"
            )
        }
    }

    private fun createPlayerNameTagTable() { // 플레이어 네임태그(칭호) 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS Player_NameTag (" +
                        "UUID VARCHAR(36) NOT NULL, " +
                        "Tag VARCHAR(255), " +
                        "PRIMARY KEY (UUID)" +
                        ")"
            )
        }
    }

    private fun createJoinQuitMessageTable() { // 입장, 퇴장 메시지 테이블
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

    private fun createSettingsTable() { // 설정 테이블
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

    private fun createHalloweenItemOwnerTable() { // 할로윈 아이템 소유자 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
            CREATE TABLE IF NOT EXISTS Halloween_Item_Owner (
                UUID VARCHAR(36) PRIMARY KEY,
                sword TINYINT(1) NOT NULL DEFAULT 0,
                pickaxe TINYINT(1) NOT NULL DEFAULT 0,
                axe TINYINT(1) NOT NULL DEFAULT 0,
                shovel TINYINT(1) NOT NULL DEFAULT 0,
                hoe TINYINT(1) NOT NULL DEFAULT 0,
                bow TINYINT(1) NOT NULL DEFAULT 0,
                fishing_rod TINYINT(1) NOT NULL DEFAULT 0,
                hammer TINYINT(1) NOT NULL DEFAULT 0,
                hat TINYINT(1) NOT NULL DEFAULT 0,
                scythe TINYINT(1) NOT NULL DEFAULT 0,
                spear TINYINT(1) NOT NULL DEFAULT 0
            );
            """.trimIndent()
            )
        }
    }
}