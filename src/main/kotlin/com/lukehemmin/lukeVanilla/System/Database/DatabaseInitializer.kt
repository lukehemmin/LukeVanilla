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
        createHalloweenItemReceiveTable()
        createChristmasItemOwnerTable()
        createChristmasItemReceiveTable()
        createValentineItemOwnerTable()
        createValentineItemReceiveTable()
        createSpringItemOwnerTable()
        createSpringItemReceiveTable()
        createTitokerMessageSettingTable()
        createVoiceChannelMessageSettingTable()
        createDynamicVoiceChannelTable()
        createSupportChatLinkTable()
        createNextseasonItemTable()
        createShopsTable()
        createValentineShieldTable()
        createPlayerItemsStateTable()
        createConnectionIPTable()
        //createLockTable() // block_locks 테이블 생성 추가 - moved to createTables()
        
        // 경고 시스템 테이블 생성
        createWarningsPlayersTable()
        createWarningsRecordsTable()
        createWarningsPardonsTable()

        // MyLand에서 관리 (청크단위 토지 시스템)
        createMyLandClaimsTable()
        createMyLandMembersTable()
        createMyLandClaimHistoryTable()

        // FarmVillage에서 관리 (농사마을 토지 시스템)
        createFarmVillagePlotsTable()
        createFarmVillagePackageItemsTable()
        createFarmVillageShopsTable()

        // PlayTime 시스템 테이블 생성
        createPlayTimeTable()

        // 다른 테이블 생성 코드 추가 가능
    }

    private fun createLockTable() {
        database.createLockTable()
    }

    private fun createSecretKeyTable() { // 비밀키 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS Secret_Key (`Code` VARCHAR(30) NOT NULL)"
            )
        }
    }

    private fun createPlayerDataTable() { // 플레이어 데이터 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS Player_Data (`UUID` VARCHAR(36) NOT NULL, `NickName` VARCHAR(30) NOT NULL, `DiscordID` VARCHAR(30), `Lastest_IP` VARCHAR(45), `IsBanned` TINYINT(1) NOT NULL DEFAULT 0, PRIMARY KEY (`UUID`))"
            )
        }
    }

    private fun createPlayerAuthTable() { // 플레이어 인증 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS Player_Auth (`UUID` VARCHAR(36) NOT NULL, `IsAuth` TINYINT(1) NOT NULL DEFAULT 0, `AuthCode` VARCHAR(6), `IsFirst` TINYINT(1) NOT NULL DEFAULT 1, PRIMARY KEY (`UUID`))"
            )
        }
    }

    private fun createPlayerNameTagTable() { // 플레이어 네임태그(칭호) 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS Player_NameTag (`UUID` VARCHAR(36) NOT NULL, `Tag` VARCHAR(255), PRIMARY KEY (`UUID`))"
            )
        }
    }

    private fun createJoinQuitMessageTable() { // 입장, 퇴장 메시지 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            // 테이블 생성
            statement.executeUpdate(
                "CREATE TABLE IF NOT EXISTS Join_Quit_Message (`service_type` VARCHAR(30) NOT NULL, `message_type` VARCHAR(30) NOT NULL, `message` TEXT NOT NULL, PRIMARY KEY (`service_type`, `message_type`))"
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
                    "INSERT INTO Join_Quit_Message (`service_type`, `message_type`, `message`) VALUES (?, ?, ?)"
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
                "CREATE TABLE IF NOT EXISTS Settings (`setting_type` VARCHAR(50) NOT NULL, `setting_value` TEXT NOT NULL, PRIMARY KEY (`setting_type`))"
            )
        }
    }

    private fun createHalloweenItemOwnerTable() { // 할로윈 아이템 소유자 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
            CREATE TABLE IF NOT EXISTS Halloween_Item_Owner (
                `UUID` VARCHAR(36) PRIMARY KEY,
                `sword` TINYINT(1) NOT NULL DEFAULT 0,
                `pickaxe` TINYINT(1) NOT NULL DEFAULT 0,
                `axe` TINYINT(1) NOT NULL DEFAULT 0,
                `shovel` TINYINT(1) NOT NULL DEFAULT 0,
                `hoe` TINYINT(1) NOT NULL DEFAULT 0,
                `bow` TINYINT(1) NOT NULL DEFAULT 0,
                `fishing_rod` TINYINT(1) NOT NULL DEFAULT 0,
                `hammer` TINYINT(1) NOT NULL DEFAULT 0,
                `hat` TINYINT(1) NOT NULL DEFAULT 0,
                `scythe` TINYINT(1) NOT NULL DEFAULT 0,
                `spear` TINYINT(1) NOT NULL DEFAULT 0
            );
            """.trimIndent()
            )
        }
    }

    private fun createHalloweenItemReceiveTable() { // 할로윈 아이템 수령 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
            CREATE TABLE IF NOT EXISTS Halloween_Item_Receive (
                `UUID` VARCHAR(36) PRIMARY KEY,
                `sword` TINYINT(1) NOT NULL DEFAULT 0,
                `pickaxe` TINYINT(1) NOT NULL DEFAULT 0,
                `axe` TINYINT(1) NOT NULL DEFAULT 0,
                `shovel` TINYINT(1) NOT NULL DEFAULT 0,
                `hoe` TINYINT(1) NOT NULL DEFAULT 0,
                `bow` TINYINT(1) NOT NULL DEFAULT 0,
                `fishing_rod` TINYINT(1) NOT NULL DEFAULT 0,
                `hammer` TINYINT(1) NOT NULL DEFAULT 0,
                `hat` TINYINT(1) NOT NULL DEFAULT 0,
                `scythe` TINYINT(1) NOT NULL DEFAULT 0,
                `spear` TINYINT(1) NOT NULL DEFAULT 0
            );
            """.trimIndent()
            )
        }
    }

    private fun createChristmasItemOwnerTable() { // 크리스마스 아이템 소유자 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
            CREATE TABLE IF NOT EXISTS Christmas_Item_Owner (
                `UUID` VARCHAR(36) PRIMARY KEY,
                `sword` TINYINT(1) NOT NULL DEFAULT 0,
                `pickaxe` TINYINT(1) NOT NULL DEFAULT 0,
                `axe` TINYINT(1) NOT NULL DEFAULT 0,
                `shovel` TINYINT(1) NOT NULL DEFAULT 0,
                `hoe` TINYINT(1) NOT NULL DEFAULT 0,
                `bow` TINYINT(1) NOT NULL DEFAULT 0,
                `crossbow` TINYINT(1) NOT NULL DEFAULT 0,
                `fishing_rod` TINYINT(1) NOT NULL DEFAULT 0,
                `hammer` TINYINT(1) NOT NULL DEFAULT 0,
                `shield` TINYINT(1) NOT NULL DEFAULT 0,
                `head` TINYINT(1) NOT NULL DEFAULT 0,
                `helmet` TINYINT(1) NOT NULL DEFAULT 0,
                `chestplate` TINYINT(1) NOT NULL DEFAULT 0,
                `leggings` TINYINT(1) NOT NULL DEFAULT 0,
                `boots` TINYINT(1) NOT NULL DEFAULT 0,
                `registered_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """.trimIndent()
            )
        }
    }

    private fun createChristmasItemReceiveTable() { // 크리스마스 아이템 수령 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
            CREATE TABLE IF NOT EXISTS Christmas_Item_Receive (
                `UUID` VARCHAR(36) PRIMARY KEY,
                `sword` TINYINT(1) NOT NULL DEFAULT 0,
                `pickaxe` TINYINT(1) NOT NULL DEFAULT 0,
                `axe` TINYINT(1) NOT NULL DEFAULT 0,
                `shovel` TINYINT(1) NOT NULL DEFAULT 0,
                `hoe` TINYINT(1) NOT NULL DEFAULT 0,
                `bow` TINYINT(1) NOT NULL DEFAULT 0,
                `crossbow` TINYINT(1) NOT NULL DEFAULT 0,
                `fishing_rod` TINYINT(1) NOT NULL DEFAULT 0,
                `hammer` TINYINT(1) NOT NULL DEFAULT 0,
                `shield` TINYINT(1) NOT NULL DEFAULT 0,
                `head` TINYINT(1) NOT NULL DEFAULT 0,
                `helmet` TINYINT(1) NOT NULL DEFAULT 0,
                `chestplate` TINYINT(1) NOT NULL DEFAULT 0,
                `leggings` TINYINT(1) NOT NULL DEFAULT 0,
                `boots` TINYINT(1) NOT NULL DEFAULT 0,
                `last_received_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """.trimIndent()
            )
        }
    }

    private fun createValentineItemOwnerTable() { // 발렌타인 아이템 소유자 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
            CREATE TABLE IF NOT EXISTS Valentine_Item_Owner (
                `UUID` VARCHAR(36) PRIMARY KEY,
                `sword` TINYINT(1) NOT NULL DEFAULT 0,
                `pickaxe` TINYINT(1) NOT NULL DEFAULT 0,
                `axe` TINYINT(1) NOT NULL DEFAULT 0,
                `shovel` TINYINT(1) NOT NULL DEFAULT 0,
                `hoe` TINYINT(1) NOT NULL DEFAULT 0,
                `fishing_rod` TINYINT(1) NOT NULL DEFAULT 0,
                `bow` TINYINT(1) NOT NULL DEFAULT 0,
                `crossbow` TINYINT(1) NOT NULL DEFAULT 0,
                `hammer` TINYINT(1) NOT NULL DEFAULT 0,
                `helmet` TINYINT(1) NOT NULL DEFAULT 0,
                `chestplate` TINYINT(1) NOT NULL DEFAULT 0,
                `leggings` TINYINT(1) NOT NULL DEFAULT 0,
                `boots` TINYINT(1) NOT NULL DEFAULT 0,
                `head` TINYINT(1) NOT NULL DEFAULT 0,
                `shield` TINYINT(1) NOT NULL DEFAULT 0,
                `registered_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """.trimIndent()
            )
        }
    }

    private fun createValentineItemReceiveTable() { // 발렌타인 아이템 수령 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
            CREATE TABLE IF NOT EXISTS Valentine_Item_Receive (
                `UUID` VARCHAR(36) PRIMARY KEY,
                `sword` TINYINT(1) NOT NULL DEFAULT 0,
                `pickaxe` TINYINT(1) NOT NULL DEFAULT 0,
                `axe` TINYINT(1) NOT NULL DEFAULT 0,
                `shovel` TINYINT(1) NOT NULL DEFAULT 0,
                `hoe` TINYINT(1) NOT NULL DEFAULT 0,
                `fishing_rod` TINYINT(1) NOT NULL DEFAULT 0,
                `bow` TINYINT(1) NOT NULL DEFAULT 0,
                `crossbow` TINYINT(1) NOT NULL DEFAULT 0,
                `hammer` TINYINT(1) NOT NULL DEFAULT 0,
                `helmet` TINYINT(1) NOT NULL DEFAULT 0,
                `chestplate` TINYINT(1) NOT NULL DEFAULT 0,
                `leggings` TINYINT(1) NOT NULL DEFAULT 0,
                `boots` TINYINT(1) NOT NULL DEFAULT 0,
                `head` TINYINT(1) NOT NULL DEFAULT 0,
                `shield` TINYINT(1) NOT NULL DEFAULT 0,
                `last_received_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """.trimIndent()
            )
        }
    }

    private fun createSpringItemOwnerTable() { // 봄 아이템 소유자 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
            CREATE TABLE IF NOT EXISTS Spring_Item_Owner (
                `UUID` VARCHAR(36) PRIMARY KEY,
                `helmet` TINYINT(1) NOT NULL DEFAULT 0,
                `chestplate` TINYINT(1) NOT NULL DEFAULT 0,
                `leggings` TINYINT(1) NOT NULL DEFAULT 0,
                `boots` TINYINT(1) NOT NULL DEFAULT 0,
                `sword` TINYINT(1) NOT NULL DEFAULT 0,
                `pickaxe` TINYINT(1) NOT NULL DEFAULT 0,
                `axe` TINYINT(1) NOT NULL DEFAULT 0,
                `hoe` TINYINT(1) NOT NULL DEFAULT 0,
                `shovel` TINYINT(1) NOT NULL DEFAULT 0,
                `bow` TINYINT(1) NOT NULL DEFAULT 0,
                `crossbow` TINYINT(1) NOT NULL DEFAULT 0,
                `shield` TINYINT(1) NOT NULL DEFAULT 0,
                `hammer` TINYINT(1) NOT NULL DEFAULT 0,
                `registered_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """.trimIndent()
            )
        }
    }

    private fun createSpringItemReceiveTable() { // 봄 아이템 수령 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
            CREATE TABLE IF NOT EXISTS Spring_Item_Receive (
                `UUID` VARCHAR(36) PRIMARY KEY,
                `helmet` TINYINT(1) NOT NULL DEFAULT 0,
                `chestplate` TINYINT(1) NOT NULL DEFAULT 0,
                `leggings` TINYINT(1) NOT NULL DEFAULT 0,
                `boots` TINYINT(1) NOT NULL DEFAULT 0,
                `sword` TINYINT(1) NOT NULL DEFAULT 0,
                `pickaxe` TINYINT(1) NOT NULL DEFAULT 0,
                `axe` TINYINT(1) NOT NULL DEFAULT 0,
                `hoe` TINYINT(1) NOT NULL DEFAULT 0,
                `shovel` TINYINT(1) NOT NULL DEFAULT 0,
                `bow` TINYINT(1) NOT NULL DEFAULT 0,
                `crossbow` TINYINT(1) NOT NULL DEFAULT 0,
                `shield` TINYINT(1) NOT NULL DEFAULT 0,
                `hammer` TINYINT(1) NOT NULL DEFAULT 0,
                `last_received_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            """.trimIndent()
            )
        }
    }

    private fun createTitokerMessageSettingTable() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
            CREATE TABLE IF NOT EXISTS Titoker_Message_Setting (
                `UUID` VARCHAR(36) PRIMARY KEY,
                `IsEnabled` BOOLEAN DEFAULT false
            )
            """
            )
        }
    }

    private fun createVoiceChannelMessageSettingTable() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
            CREATE TABLE IF NOT EXISTS Voice_Channel_Message_Setting (
                `UUID` VARCHAR(36) PRIMARY KEY,
                `IsEnabled` BOOLEAN DEFAULT false
            )
            """
            )
        }
    }

    private fun createDynamicVoiceChannelTable() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
            CREATE TABLE IF NOT EXISTS Dynamic_Voice_Channel (
                `channel_id` VARCHAR(30) PRIMARY KEY,
                `creator_id` VARCHAR(30) NOT NULL,
                `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """
            )
        }
    }

    private fun createSupportChatLinkTable() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
            CREATE TABLE IF NOT EXISTS SupportChatLink (
                `UUID` VARCHAR(36) NOT NULL,
                `SupportID` VARCHAR(20) NOT NULL,
                `CaseClose` TINYINT(1) NOT NULL DEFAULT 0,
                `MessageLink` TEXT,
                PRIMARY KEY (`SupportID`)
            )
            """.trimIndent()
            )
        }
    }

    private fun createNextseasonItemTable() { // 다음 시즌 아이템 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
            CREATE TABLE IF NOT EXISTS Nextseason_Item (
                `UUID` VARCHAR(36) NOT NULL,
                `Item_Type` VARCHAR(50) NOT NULL,
                `Item_Data` LONGTEXT,
                PRIMARY KEY (`UUID`)
            );
            """.trimIndent()
            )
        }
    }

    private fun createShopsTable() { // 상점 테이블
        database.getConnection().use { connection ->
            connection.createStatement().use { statement ->
                // shops 테이블 생성
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS shops (
                        `name` VARCHAR(255) PRIMARY KEY,
                        `npc_id` INT NOT NULL,
                        `rows` INT DEFAULT 3
                    );
                    """
                )

                // shop_items 테이블 생성
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS shop_items (
                        `shop_name` VARCHAR(255) NOT NULL,
                        `slot` INT NOT NULL,
                        `item_type` VARCHAR(255),
                        `item_meta` VARCHAR(255),
                        `buy_price` DECIMAL(20, 2),
                        `sell_price` DECIMAL(20, 2),
                        PRIMARY KEY (`shop_name`, `slot`),
                        FOREIGN KEY (`shop_name`) REFERENCES shops(`name`) ON DELETE CASCADE
                    );
                    """
                )
            }
        }
    }

    private fun createValentineShieldTable() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS Valentine_Shield (
                    `UUID` VARCHAR(36) PRIMARY KEY,
                    `received` TINYINT(1) NOT NULL DEFAULT 0
                );
                """.trimIndent()
            )
        }
    }

    private fun createPlayerItemsStateTable() { // 플레이어 아이템 상태 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS Player_Items_State (
                    `UUID` VARCHAR(36) NOT NULL,
                    `ItemID` VARCHAR(255) NOT NULL,
                    `State` VARCHAR(50) NOT NULL,
                    PRIMARY KEY (`UUID`, `ItemID`)
                );
                """.trimIndent()
            )
        }
    }
    
    private fun createConnectionIPTable() { // 유저 IP 접속 기록 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS Connection_IP (
                    `id` INT AUTO_INCREMENT PRIMARY KEY,
                    `UUID` VARCHAR(36) NOT NULL,
                    `NickName` VARCHAR(30) NOT NULL,
                    `IP` VARCHAR(45) NOT NULL,
                    `ConnectedAt` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    INDEX `idx_uuid` (`UUID`),
                    INDEX `idx_ip` (`IP`)
                );
                """.trimIndent()
            )
        }
    }
    
    private fun createWarningsPlayersTable() { // 플레이어 경고 정보 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS warnings_players (
                    `player_id` INT AUTO_INCREMENT PRIMARY KEY,
                    `uuid` VARCHAR(36) UNIQUE NOT NULL,
                    `username` VARCHAR(50) NOT NULL,
                    `last_warning_date` DATETIME,
                    `active_warnings_count` INT DEFAULT 0,
                    INDEX `idx_uuid` (`uuid`)
                );
                """.trimIndent()
            )
        }
    }
    
    private fun createWarningsRecordsTable() { // 경고 내역 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS warnings_records (
                    `warning_id` INT AUTO_INCREMENT PRIMARY KEY,
                    `player_id` INT NOT NULL,
                    `admin_uuid` VARCHAR(36) NOT NULL,
                    `admin_name` VARCHAR(50) NOT NULL,
                    `reason` TEXT NOT NULL,
                    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
                    `is_active` BOOLEAN DEFAULT TRUE,
                    `pardoned_at` DATETIME NULL,
                    `pardoned_by_uuid` VARCHAR(36) NULL,
                    `pardoned_by_name` VARCHAR(50) NULL,
                    `pardon_reason` TEXT NULL,
                    FOREIGN KEY (`player_id`) REFERENCES `warnings_players` (`player_id`) ON DELETE CASCADE,
                    INDEX `idx_player_active` (`player_id`, `is_active`)
                );
                """.trimIndent()
            )
        }
    }
    
    private fun createWarningsPardonsTable() { // 경고 차감 이력 테이블
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS warnings_pardons (
                    `pardon_id` INT AUTO_INCREMENT PRIMARY KEY,
                    `player_id` INT NOT NULL,
                    `admin_uuid` VARCHAR(36) NOT NULL,
                    `admin_name` VARCHAR(50) NOT NULL,
                    `count` INT NOT NULL DEFAULT 1,
                    `reason` TEXT NOT NULL,
                    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
                    `is_id_based` BOOLEAN NOT NULL,
                    `warning_id` INT NULL,
                    FOREIGN KEY (`player_id`) REFERENCES `warnings_players` (`player_id`) ON DELETE CASCADE,
                    FOREIGN KEY (`warning_id`) REFERENCES `warnings_records` (`warning_id`) ON DELETE SET NULL,
                    INDEX `idx_player_id` (`player_id`)
                );
                """.trimIndent()
            )
        }
    }

    private fun createMyLandClaimsTable(){
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS myland_claims (
                    `world` VARCHAR(255) NOT NULL,
                    `chunk_x` INT NOT NULL,
                    `chunk_z` INT NOT NULL,
                    `owner_uuid` VARCHAR(36) NOT NULL,
                    `claim_type` VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
                    `claimed_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (`world`, `chunk_x`, `chunk_z`)
                );
                """.trimIndent()
            )
        }
    }

    private fun createMyLandMembersTable() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS myland_members (
                    `world` VARCHAR(255) NOT NULL,
                    `chunk_x` INT NOT NULL,
                    `chunk_z` INT NOT NULL,
                    `member_uuid` VARCHAR(36) NOT NULL,
                    PRIMARY KEY (`world`, `chunk_x`, `chunk_z`, `member_uuid`)
                );
                """.trimIndent()
            )
        }
    }

    private fun createFarmVillageShopsTable() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS farmvillage_shops (
                    `shop_id` VARCHAR(100) PRIMARY KEY,
                    `world` VARCHAR(255) NOT NULL,
                    `top_block_x` INT NOT NULL,
                    `top_block_y` INT NOT NULL,
                    `top_block_z` INT NOT NULL,
                    `bottom_block_x` INT NOT NULL,
                    `bottom_block_y` INT NOT NULL,
                    `bottom_block_z` INT NOT NULL
                );
                """.trimIndent()
            )
        }
    }

    private fun createMyLandClaimHistoryTable() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS myland_claim_history (
                    `history_id` INT AUTO_INCREMENT PRIMARY KEY,
                    `world` VARCHAR(255) NOT NULL,
                    `chunk_x` INT NOT NULL,
                    `chunk_z` INT NOT NULL,
                    `previous_owner_uuid` VARCHAR(36) NOT NULL,
                    `actor_uuid` VARCHAR(36),
                    `reason` VARCHAR(255) NOT NULL,
                    `unclaimed_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                """.trimIndent()
            )
        }
    }

    private fun createFarmVillagePlotsTable() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS farmvillage_plots (
                    `plot_number` INT NOT NULL,
                    `plot_part` INT NOT NULL,
                    `world` VARCHAR(255) NOT NULL,
                    `chunk_x` INT NOT NULL,
                    `chunk_z` INT NOT NULL,
                    PRIMARY KEY (`plot_number`, `plot_part`)
                );
                """.trimIndent()
            )
        }
    }

    private fun createFarmVillagePackageItemsTable() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS farmvillage_package_items (
                    `slot` INT PRIMARY KEY,
                    `item_type` VARCHAR(50) NOT NULL,
                    `item_identifier` TEXT NOT NULL,
                    `item_data` JSON
                );
                """.trimIndent()
            )
        }
    }

    private fun createPlayTimeTable() {
        database.getConnection().use { connection ->
            val statement = connection.createStatement()
            statement.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS playtime_data (
                    `player_uuid` VARCHAR(36) PRIMARY KEY,
                    `total_playtime_seconds` BIGINT NOT NULL DEFAULT 0,
                    `session_start_time` BIGINT NULL,
                    `last_updated` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    `created_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                );
                """.trimIndent()
            )
        }
    }
}
