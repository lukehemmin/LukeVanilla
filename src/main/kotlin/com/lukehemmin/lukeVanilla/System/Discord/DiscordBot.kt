package com.lukehemmin.lukeVanilla.System.Discord

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import javax.security.auth.login.LoginException

class DiscordBot : ListenerAdapter() {

    lateinit var jda: JDA
        private set

    fun start(token: String) {
        try {
            jda = JDABuilder.createDefault(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(this)
                .build()
            jda.awaitReady()
            println("Discord 봇이 시작되었습니다.")
        } catch (e: LoginException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}