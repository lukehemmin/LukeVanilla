package com.lukehemmin.lukeVanilla.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier
import com.velocitypowered.api.command.CommandManager
import com.velocitypowered.api.command.CommandMeta
import com.velocitypowered.api.command.SimpleCommand
import org.slf4j.Logger
import java.nio.file.Path

@Plugin(
    id = "lukevanilla",
    name = "LukeVanilla Velocity",
    version = "1.0-SNAPSHOT",
    description = "LukeVanilla Velocity plugin",
    authors = ["lukehemmin"]
)
class VelocityMain @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    @DataDirectory private val dataDirectory: Path
) {
    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        logger.info("LukeVanilla Velocity plugin has been enabled!")
        
        // 채널 등록
        val channel = MinecraftChannelIdentifier.create("luke", "vanilla_status")
        server.channelRegistrar.register(channel)
        
        // 명령어 등록
        registerCommands()
        
        logger.info("Automatic server redirection system initialized")
    }
    
    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        logger.info("LukeVanilla Velocity plugin has been disabled!")
    }
    
    /**
     * 명령어 등록
     */
    private fun registerCommands() {
        val commandManager: CommandManager = server.commandManager

        // /로비서버 명령어 등록 (다른 별칭 없이 오직 "/로비서버"만 사용)
        val lobbyCommandMeta: CommandMeta = commandManager.metaBuilder("로비서버")
            .plugin(this)
            .build()
        
        commandManager.register(lobbyCommandMeta, object : SimpleCommand {
            override fun execute(invocation: SimpleCommand.Invocation) {
                logger.info("로비서버 명령어 실행")
            }
        })

        // /야생서버 명령어 등록 (다른 별칭 없이 오직 "/야생서버"만 사용)
        val wildServerCommandMeta: CommandMeta = commandManager.metaBuilder("야생서버")
            .plugin(this)
            .build()
            
        commandManager.register(wildServerCommandMeta, object : SimpleCommand {
            override fun execute(invocation: SimpleCommand.Invocation) {
                logger.info("야생서버 명령어 실행")
            }
        })

        logger.info("Successfully registered commands: /로비서버, /야생서버")
    }
}