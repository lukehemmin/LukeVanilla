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
            // 테이블 생성
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS Join_Quit_Message (" +
                        "service_type VARCHAR(30) NOT NULL, " +
                        "message_type VARCHAR(30) NOT NULL, " +
                        "message TEXT NOT NULL, " +
                        "PRIMARY KEY (service_type, message_type)" +
                        ")"
            )

            // 테이블이 비어 있는지 확인
            val resultSet = statement.executeQuery("SELECT COUNT(*) AS count FROM Join_Quit_Message")
            var isEmpty = true
            if (resultSet.next()) {
                isEmpty = resultSet.getInt("count") == 0
            }

            // 테이블이 비어 있다면 기본값 삽입
            if (isEmpty) {
                val insertStmt = connection.prepareStatement(
                    "INSERT INTO Join_Quit_Message (service_type, message_type, message) VALUES (?, ?, ?)"
                )

                // 기본값 데이터 리스트
                val defaultMessages = listOf(
                    Triple("Lobby", "LobbyJoinMessage", "§b§l{playerName} §a§l님, 이곳은 로비서버에요!\n\n§a§l이곳은 서버 점검, 패치가 있을때 서버가 다시 열릴때까지 기다리는 서버에요!\n§a§l서버가 열리면 다시 원래 서버, 원래 위치로 돌아가게 되요!\n§a§l잠시만 기다려주세요! ( 예정시간은 공지사항이나 패치노트에 있어요! )"),
                    Triple("Lobby", "LobbyServerJoin", "       §f§l[§a§l+§f§l] §f§l{playerName} 님이 로비 서버에 접속했습니다!"),
                    Triple("Lobby", "LobbyServerQuit", "       §f§l[§c§l-§f§l] §f§l{playerName} 님이 로비 서버에서 나갔습니다!"),
                    Triple("Vanilla", "VanillaJoinMessage", "            §b§l{playerName} §a§l님, 오늘도 서버에 오셨네요! 반가워요!\n"),
                    Triple("Vanilla", "VanillaServerFirstJoin", "       §f§l[§e§l++§f§l] §f§l{playerName} 님이 처음으로 서버에 접속했습니다!"),
                    Triple("Vanilla", "VanillaServerJoin", "       §f§l[§a§l+§f§l] §f§l{playerName} 님이 서버에 접속했습니다!"),
                    Triple("Vanilla", "VanillaServerQuit", "       §f§l[§c§l-§f§l] §f§l{playerName} 님이 서버에서 나갔습니다!")
                )

                for ((serviceType, messageType, message) in defaultMessages) {
                    insertStmt.setString(1, serviceType)
                    insertStmt.setString(2, messageType)
                    insertStmt.setString(3, message)
                    insertStmt.executeUpdate()
                }
            }
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