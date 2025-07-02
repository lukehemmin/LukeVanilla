package com.lukehemmin.lukeVanilla.System.Discord

import com.lukehemmin.lukeVanilla.System.Database.Database
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import java.util.EnumSet

/**
 * Listener that creates temporary voice channels when a user joins a trigger channel.
 * The trigger channel and target category IDs are loaded from the Settings table.
 */
class DynamicVoiceChannelManager(private val database: Database) : ListenerAdapter() {

    private val createdChannels = mutableSetOf<String>()

    // Trigger voice channel IDs
    private val triggerChannel1 = database.getSettingValue("TRIGGER_VOICE_CHANNEL_ID_1")
    private val triggerChannel2 = database.getSettingValue("TRIGGER_VOICE_CHANNEL_ID_2")

    // Category IDs where the new channels will be created
    private val category1 = database.getSettingValue("VOICE_CATEGORY_ID_1")
    private val category2 = database.getSettingValue("VOICE_CATEGORY_ID_2")

    override fun onGuildVoiceUpdate(event: GuildVoiceUpdateEvent) {
        val member = event.member
        val guild = event.guild
        val joined = event.channelJoined?.asVoiceChannel()
        val left = event.channelLeft?.asVoiceChannel()

        // When joining a trigger channel, create a new voice channel for the user
        if (joined != null) {
            val categoryId = when (joined.id) {
                triggerChannel1 -> category1
                triggerChannel2 -> category2
                else -> null
            }

            if (categoryId != null) {
                val category = guild.getCategoryById(categoryId)
                category?.createVoiceChannel("${member.effectiveName}의 방")
                    ?.addPermissionOverride(
                        member,
                        EnumSet.of(
                            Permission.VIEW_CHANNEL,
                            Permission.MANAGE_CHANNEL,
                            Permission.MANAGE_PERMISSIONS,
                            Permission.VOICE_CONNECT
                        ),
                        null
                    )
                    ?.queue { channel ->
                        createdChannels.add(channel.id)
                        guild.moveVoiceMember(member, channel).queue()
                    }
            }
        }

        // Remove empty created channels when everyone leaves
        if (left != null && createdChannels.contains(left.id)) {
            if (left.members.isEmpty()) {
                createdChannels.remove(left.id)
                left.delete().queue()
            }
        }
    }
}

