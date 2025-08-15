package com.lukehemmin.lukeVanilla.System.BookSystem

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.persistence.PersistentDataType
import java.util.*
import java.util.logging.Logger

/**
 * 책 아이템 관리 유틸리티 클래스
 * NBT 태그를 통해 책과 깃펜에 고유 ID를 부여하고 관리합니다.
 */
class BookItemManager(
    private val plugin: com.lukehemmin.lukeVanilla.Main,
    private val logger: Logger
) {
    
    private val bookIdKey = NamespacedKey(plugin, "book_id")
    private val bookOwnerKey = NamespacedKey(plugin, "book_owner")
    private val bookVersionKey = NamespacedKey(plugin, "book_version")
    
    /**
     * 아이템에서 책 ID를 가져옵니다.
     */
    fun getBookId(item: ItemStack): Long? {
        val meta = item.itemMeta ?: return null
        val container = meta.persistentDataContainer
        
        return if (container.has(bookIdKey, PersistentDataType.LONG)) {
            container.get(bookIdKey, PersistentDataType.LONG)
        } else null
    }
    
    /**
     * BookMeta에서 책 ID를 가져옵니다.
     */
    fun getBookIdFromMeta(meta: BookMeta): Long? {
        val container = meta.persistentDataContainer
        
        return if (container.has(bookIdKey, PersistentDataType.LONG)) {
            container.get(bookIdKey, PersistentDataType.LONG)
        } else null
    }
    
    /**
     * 아이템에서 책 소유자를 가져옵니다.
     */
    fun getBookOwner(item: ItemStack): String? {
        val meta = item.itemMeta ?: return null
        val container = meta.persistentDataContainer
        
        return container.get(bookOwnerKey, PersistentDataType.STRING)
    }
    
    /**
     * 아이템에서 책 버전을 가져옵니다.
     */
    fun getBookVersion(item: ItemStack): Int {
        val meta = item.itemMeta ?: return 0
        val container = meta.persistentDataContainer
        
        return container.get(bookVersionKey, PersistentDataType.INTEGER) ?: 0
    }
    
    /**
     * 아이템에 책 ID를 설정합니다.
     */
    fun setBookId(item: ItemStack, bookId: Long, ownerUuid: String): ItemStack {
        val meta = item.itemMeta ?: return item
        val container = meta.persistentDataContainer
        
        container.set(bookIdKey, PersistentDataType.LONG, bookId)
        container.set(bookOwnerKey, PersistentDataType.STRING, ownerUuid)
        container.set(bookVersionKey, PersistentDataType.INTEGER, 1)
        
        item.itemMeta = meta
        return item
    }
    
    /**
     * BookMeta에 책 ID를 설정합니다.
     */
    fun setBookIdInMeta(meta: BookMeta, bookId: Long, ownerUuid: String): BookMeta {
        val container = meta.persistentDataContainer
        
        container.set(bookIdKey, PersistentDataType.LONG, bookId)
        container.set(bookOwnerKey, PersistentDataType.STRING, ownerUuid)
        container.set(bookVersionKey, PersistentDataType.INTEGER, 1)
        
        return meta
    }
    
    /**
     * BookMeta에서 버전을 증가시킵니다.
     */
    fun incrementVersionInMeta(meta: BookMeta, bookId: Long, ownerUuid: String): BookMeta {
        val container = meta.persistentDataContainer
        
        val currentVersion = container.get(bookVersionKey, PersistentDataType.INTEGER) ?: 0
        container.set(bookVersionKey, PersistentDataType.INTEGER, currentVersion + 1)
        container.set(bookIdKey, PersistentDataType.LONG, bookId)
        container.set(bookOwnerKey, PersistentDataType.STRING, ownerUuid)
        
        return meta
    }
    
    /**
     * BookMeta에서 버전을 가져옵니다.
     */
    fun getVersionFromMeta(meta: BookMeta): Int {
        val container = meta.persistentDataContainer
        return container.get(bookVersionKey, PersistentDataType.INTEGER) ?: 0
    }
    
    /**
     * BookMeta에서 소유자를 가져옵니다.
     */
    fun getOwnerFromMeta(meta: BookMeta): String? {
        val container = meta.persistentDataContainer
        return container.get(bookOwnerKey, PersistentDataType.STRING)
    }
    
    /**
     * 아이템의 버전을 증가시킵니다.
     */
    fun incrementVersion(item: ItemStack): ItemStack {
        val meta = item.itemMeta ?: return item
        val container = meta.persistentDataContainer
        
        val currentVersion = container.get(bookVersionKey, PersistentDataType.INTEGER) ?: 0
        container.set(bookVersionKey, PersistentDataType.INTEGER, currentVersion + 1)
        
        item.itemMeta = meta
        return item
    }
    
    /**
     * 책 아이템인지 확인합니다.
     */
    fun isBookItem(item: ItemStack): Boolean {
        return item.type == Material.WRITABLE_BOOK || item.type == Material.WRITTEN_BOOK
    }
    
    /**
     * 아이템이 책 시스템에 등록된 책인지 확인합니다.
     */
    fun isRegisteredBook(item: ItemStack): Boolean {
        return isBookItem(item) && getBookId(item) != null
    }
    
    /**
     * 플레이어가 이 책의 소유자인지 확인합니다.
     */
    fun isBookOwner(item: ItemStack, playerUuid: String): Boolean {
        val ownerUuid = getBookOwner(item)
        return ownerUuid == playerUuid
    }
    
    /**
     * 새로운 책과 깃펜을 플레이어에게 줍니다.
     */
    fun giveNewBookAndQuill(player: Player): ItemStack {
        val item = ItemStack(Material.WRITABLE_BOOK)
        val meta = item.itemMeta as BookMeta
        
        // 기본 제목 설정 (선택사항)
        meta.title = "새로운 책"
        
        item.itemMeta = meta
        
        // 플레이어 인벤토리에 추가
        player.inventory.addItem(item)
        
        return item
    }
    
    /**
     * 책 아이템에 메타데이터를 추가하여 사용자에게 정보 표시
     */
    fun updateBookLore(item: ItemStack, bookData: BookData): ItemStack {
        val meta = item.itemMeta ?: return item
        
        val lore = mutableListOf<String>()
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━")
        lore.add("§a📚 책 시스템 정보")
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━")
        lore.add("§f책 ID: §e${bookData.id}")
        lore.add("§f상태: ${if (bookData.isPublic) "§a공개" else "§7비공개"}")
        lore.add("§f페이지: §e${bookData.pageCount}")
        lore.add("§f시즌: §b${bookData.season ?: "미분류"}")
        lore.add("§f버전: §e${getBookVersion(item)}")
        lore.add("§7━━━━━━━━━━━━━━━━━━━━━━")
        lore.add("§7이 책은 자동으로 저장됩니다.")
        
        meta.lore = lore
        item.itemMeta = meta
        
        return item
    }
    
    /**
     * 책 아이템에서 로어를 제거합니다 (깔끔한 저장용)
     */
    fun cleanBookLore(item: ItemStack): ItemStack {
        val meta = item.itemMeta ?: return item
        meta.lore = null
        item.itemMeta = meta
        return item
    }
    
    /**
     * 디버그용: 아이템의 모든 NBT 정보를 출력
     */
    fun debugBookItem(item: ItemStack, player: Player) {
        if (!isBookItem(item)) {
            player.sendMessage("§c이 아이템은 책이 아닙니다.")
            return
        }
        
        val bookId = getBookId(item)
        val owner = getBookOwner(item)
        val version = getBookVersion(item)
        
        player.sendMessage("§a=== 책 디버그 정보 ===")
        player.sendMessage("§f아이템 타입: §e${item.type}")
        player.sendMessage("§f책 ID: §e${bookId ?: "없음"}")
        player.sendMessage("§f소유자: §e${owner ?: "없음"}")
        player.sendMessage("§f버전: §e$version")
        player.sendMessage("§f등록된 책: ${if (isRegisteredBook(item)) "§a예" else "§c아니오"}")
        player.sendMessage("§f소유자 일치: ${if (isBookOwner(item, player.uniqueId.toString())) "§a예" else "§c아니오"}")
    }
}