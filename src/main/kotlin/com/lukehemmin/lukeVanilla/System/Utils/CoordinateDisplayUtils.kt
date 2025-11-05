package com.lukehemmin.lukeVanilla.System.Utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Chunk
import org.bukkit.Location
import org.bukkit.World
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 좌표 표시를 개선하는 유틸리티 클래스
 * 다양한 형태의 좌표 표시와 인터랙티브 기능을 제공합니다.
 */
object CoordinateDisplayUtils {

    /**
     * 기본 스타일의 향상된 좌표 표시
     */
    fun formatBasicCoordinates(chunk: Chunk, includeWorld: Boolean = true): Component {
        val worldCoordX = chunk.x * 16 + 8  // 청크 중심 좌표
        val worldCoordZ = chunk.z * 16 + 8

        return Component.text()
            .append(
                if (includeWorld) {
                    Component.text("${getWorldDisplayName(chunk.world)} ", getWorldColor(chunk.world))
                } else {
                    Component.empty()
                }
            )
            .append(Component.text("📍 ", NamedTextColor.YELLOW))
            .append(Component.text("청크 ", NamedTextColor.GRAY))
            .append(Component.text("(${chunk.x}, ${chunk.z})", NamedTextColor.WHITE, TextDecoration.BOLD))
            .append(Component.text(" ≈ ", NamedTextColor.DARK_GRAY))
            .append(Component.text("좌표 ", NamedTextColor.GRAY))
            .append(Component.text("(${worldCoordX}, ${worldCoordZ})", NamedTextColor.GREEN, TextDecoration.BOLD))
            .build()
    }

    /**
     * 클릭 가능한 좌표 표시 (좌표 복사 기능 포함)
     */
    fun formatClickableCoordinates(chunk: Chunk, includeWorld: Boolean = true): Component {
        val worldCoordX = chunk.x * 16 + 8
        val worldCoordZ = chunk.z * 16 + 8
        val coordString = "${worldCoordX} ~ ${worldCoordZ}"
        val copyCommand = "/trigger coordinates set 1"  // 예시 명령어

        return Component.text()
            .append(
                if (includeWorld) {
                    Component.text("${getWorldDisplayName(chunk.world)} ", getWorldColor(chunk.world))
                } else {
                    Component.empty()
                }
            )
            .append(Component.text("📍 ", NamedTextColor.YELLOW))
            .append(
                Component.text("(${chunk.x}, ${chunk.z})", NamedTextColor.WHITE, TextDecoration.BOLD)
                    .hoverEvent(HoverEvent.showText(
                        Component.text()
                            .append(Component.text("📋 청크 좌표 복사하기", NamedTextColor.GREEN, TextDecoration.BOLD))
                            .append(Component.newline())
                            .append(Component.text("클릭하여 청크 좌표를 채팅창에 입력", NamedTextColor.GRAY))
                            .build()
                    ))
                    .clickEvent(ClickEvent.suggestCommand("${chunk.x} ${chunk.z}"))
            )
            .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
            .append(
                Component.text("(${worldCoordX}, ${worldCoordZ})", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .hoverEvent(HoverEvent.showText(
                        Component.text()
                            .append(Component.text("🎯 월드 좌표 복사하기", NamedTextColor.AQUA, TextDecoration.BOLD))
                            .append(Component.newline())
                            .append(Component.text("클릭하여 월드 좌표를 채팅창에 입력", NamedTextColor.GRAY))
                            .append(Component.newline())
                            .append(Component.text("텔레포트: /tp @s ${worldCoordX} ~ ${worldCoordZ}", NamedTextColor.YELLOW))
                            .build()
                    ))
                    .clickEvent(ClickEvent.suggestCommand("/tp @s ${worldCoordX} ~ ${worldCoordZ}"))
            )
            .build()
    }

    /**
     * 상세한 좌표 정보 표시 (방향, 거리 등 포함)
     */
    fun formatDetailedCoordinates(chunk: Chunk, referenceLocation: Location? = null): Component {
        val worldCoordX = chunk.x * 16 + 8
        val worldCoordZ = chunk.z * 16 + 8

        val baseComponent = Component.text()
            .append(Component.text("🗺️ ", NamedTextColor.GOLD))
            .append(Component.text("상세 좌표 정보", NamedTextColor.WHITE, TextDecoration.BOLD))
            .append(Component.newline())
            .append(Component.text("   🌍 월드: ", NamedTextColor.GRAY))
            .append(Component.text(getWorldDisplayName(chunk.world), getWorldColor(chunk.world), TextDecoration.BOLD))
            .append(Component.newline())
            .append(Component.text("   📐 청크: ", NamedTextColor.GRAY))
            .append(Component.text("X=${chunk.x}, Z=${chunk.z}", NamedTextColor.WHITE))
            .append(Component.text(" (", NamedTextColor.DARK_GRAY))
            .append(Component.text("${getQuadrant(chunk.x, chunk.z)}", getQuadrantColor(chunk.x, chunk.z)))
            .append(Component.text(")", NamedTextColor.DARK_GRAY))
            .append(Component.newline())
            .append(Component.text("   🎯 좌표: ", NamedTextColor.GRAY))
            .append(Component.text("X=${worldCoordX}, Z=${worldCoordZ}", NamedTextColor.GREEN))
        
        // 참조 위치가 있으면 거리와 방향 정보 추가
        return if (referenceLocation != null && referenceLocation.world == chunk.world) {
            val distance = calculateDistance(referenceLocation.blockX, referenceLocation.blockZ, worldCoordX, worldCoordZ)
            val direction = calculateDirection(referenceLocation.blockX, referenceLocation.blockZ, worldCoordX, worldCoordZ)
            
            baseComponent
                .append(Component.newline())
                .append(Component.text("   📏 거리: ", NamedTextColor.GRAY))
                .append(Component.text("${String.format("%.1f", distance)}블록", NamedTextColor.YELLOW))
                .append(Component.text(" (", NamedTextColor.DARK_GRAY))
                .append(Component.text(direction.icon, direction.color))
                .append(Component.text(" ${direction.name}", direction.color))
                .append(Component.text(")", NamedTextColor.DARK_GRAY))
                .build()
        } else {
            baseComponent.build()
        }
    }

    /**
     * 컴팩트한 좌표 표시 (목록용)
     */
    fun formatCompactCoordinates(chunk: Chunk, index: Int? = null): Component {
        val worldCoordX = chunk.x * 16 + 8
        val worldCoordZ = chunk.z * 16 + 8

        val baseBuilder = Component.text()
        
        if (index != null) {
            baseBuilder.append(Component.text("${index}. ", NamedTextColor.GOLD))
        }
        
        return baseBuilder
            .append(Component.text("${getWorldIcon(chunk.world)} ", getWorldColor(chunk.world)))
            .append(Component.text("${chunk.x}", NamedTextColor.WHITE))
            .append(Component.text(", ", NamedTextColor.DARK_GRAY))
            .append(Component.text("${chunk.z}", NamedTextColor.WHITE))
            .append(Component.text(" (", NamedTextColor.DARK_GRAY))
            .append(Component.text("${worldCoordX}", NamedTextColor.GREEN))
            .append(Component.text(", ", NamedTextColor.DARK_GRAY))
            .append(Component.text("${worldCoordZ}", NamedTextColor.GREEN))
            .append(Component.text(")", NamedTextColor.DARK_GRAY))
            .build()
    }

    /**
     * 영역 표시 (여러 청크를 하나의 영역으로)
     */
    fun formatAreaCoordinates(chunks: List<Chunk>): Component {
        if (chunks.isEmpty()) {
            return Component.text("빈 영역", NamedTextColor.GRAY)
        }

        if (chunks.size == 1) {
            return formatBasicCoordinates(chunks.first())
        }

        val minX = chunks.minOf { it.x }
        val maxX = chunks.maxOf { it.x }
        val minZ = chunks.minOf { it.z }
        val maxZ = chunks.maxOf { it.z }

        val centerChunkX = (minX + maxX) / 2
        val centerChunkZ = (minZ + maxZ) / 2
        val centerWorldX = centerChunkX * 16 + 8
        val centerWorldZ = centerChunkZ * 16 + 8

        val world = chunks.first().world
        val isAllSameWorld = chunks.all { it.world == world }

        return Component.text()
            .append(Component.text("🏠️ ", NamedTextColor.GOLD))
            .append(Component.text("연결된 영역 ", NamedTextColor.WHITE, TextDecoration.BOLD))
            .append(Component.text("(${chunks.size}개 청크)", NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.text("   📏 범위: ", NamedTextColor.GRAY))
            .append(Component.text("청크 ", NamedTextColor.DARK_GRAY))
            .append(Component.text("(${minX}, ${minZ})", NamedTextColor.WHITE))
            .append(Component.text(" ~ ", NamedTextColor.DARK_GRAY))
            .append(Component.text("(${maxX}, ${maxZ})", NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text("   🎯 중심: ", NamedTextColor.GRAY))
            .append(
                if (isAllSameWorld) {
                    Component.text("${getWorldDisplayName(world)} ", getWorldColor(world))
                } else {
                    Component.text("다중 월드 ", NamedTextColor.LIGHT_PURPLE)
                }
            )
            .append(Component.text("(${centerWorldX}, ${centerWorldZ})", NamedTextColor.GREEN, TextDecoration.BOLD))
            .build()
    }

    // === 유틸리티 메서드들 ===

    private fun getWorldDisplayName(world: World): String {
        return when (world.name) {
            "world" -> "오버월드"
            "world_nether" -> "네더"
            "world_the_end" -> "엔드"
            else -> world.name
        }
    }

    private fun getWorldColor(world: World): NamedTextColor {
        return when (world.name) {
            "world" -> NamedTextColor.GREEN
            "world_nether" -> NamedTextColor.RED
            "world_the_end" -> NamedTextColor.DARK_PURPLE
            else -> NamedTextColor.BLUE
        }
    }

    private fun getWorldIcon(world: World): String {
        return when (world.name) {
            "world" -> "🌍"
            "world_nether" -> "🔥"
            "world_the_end" -> "🌌"
            else -> "🗺️"
        }
    }

    private fun getQuadrant(chunkX: Int, chunkZ: Int): String {
        return when {
            chunkX >= 0 && chunkZ >= 0 -> "북동"
            chunkX < 0 && chunkZ >= 0 -> "북서"
            chunkX < 0 && chunkZ < 0 -> "남서"
            else -> "남동"
        }
    }

    private fun getQuadrantColor(chunkX: Int, chunkZ: Int): NamedTextColor {
        return when {
            chunkX >= 0 && chunkZ >= 0 -> NamedTextColor.YELLOW  // 북동
            chunkX < 0 && chunkZ >= 0 -> NamedTextColor.AQUA    // 북서
            chunkX < 0 && chunkZ < 0 -> NamedTextColor.BLUE     // 남서
            else -> NamedTextColor.LIGHT_PURPLE                 // 남동
        }
    }

    private fun calculateDistance(x1: Int, z1: Int, x2: Int, z2: Int): Double {
        val dx = (x2 - x1).toDouble()
        val dz = (z2 - z1).toDouble()
        return sqrt(dx * dx + dz * dz)
    }

    private fun calculateDirection(fromX: Int, fromZ: Int, toX: Int, toZ: Int): Direction {
        val dx = toX - fromX
        val dz = toZ - fromZ

        // 8방향 계산
        val angle = Math.toDegrees(Math.atan2(dx.toDouble(), -dz.toDouble()))
        val normalizedAngle = (angle + 360) % 360

        return when {
            normalizedAngle >= 337.5 || normalizedAngle < 22.5 -> Direction.NORTH
            normalizedAngle >= 22.5 && normalizedAngle < 67.5 -> Direction.NORTHEAST
            normalizedAngle >= 67.5 && normalizedAngle < 112.5 -> Direction.EAST
            normalizedAngle >= 112.5 && normalizedAngle < 157.5 -> Direction.SOUTHEAST
            normalizedAngle >= 157.5 && normalizedAngle < 202.5 -> Direction.SOUTH
            normalizedAngle >= 202.5 && normalizedAngle < 247.5 -> Direction.SOUTHWEST
            normalizedAngle >= 247.5 && normalizedAngle < 292.5 -> Direction.WEST
            else -> Direction.NORTHWEST
        }
    }

    /**
     * 방향 정보 데이터 클래스
     */
    data class Direction(
        val name: String,
        val icon: String,
        val color: NamedTextColor
    ) {
        companion object {
            val NORTH = Direction("북쪽", "⬆️", NamedTextColor.WHITE)
            val NORTHEAST = Direction("북동쪽", "↗️", NamedTextColor.YELLOW)
            val EAST = Direction("동쪽", "➡️", NamedTextColor.GOLD)
            val SOUTHEAST = Direction("남동쪽", "↘️", NamedTextColor.LIGHT_PURPLE)
            val SOUTH = Direction("남쪽", "⬇️", NamedTextColor.BLUE)
            val SOUTHWEST = Direction("남서쪽", "↙️", NamedTextColor.AQUA)
            val WEST = Direction("서쪽", "⬅️", NamedTextColor.GREEN)
            val NORTHWEST = Direction("북서쪽", "↖️", NamedTextColor.GRAY)
        }
    }

    /**
     * 좌표 스타일 옵션
     */
    enum class CoordinateStyle {
        BASIC,      // 기본 스타일
        CLICKABLE,  // 클릭 가능한 스타일
        DETAILED,   // 상세 정보 포함
        COMPACT,    // 컴팩트 스타일
        AREA        // 영역 표시 스타일
    }
}