package com.lukehemmin.lukeVanilla.System.Command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin

class ReloadCommand(private val plugin: Plugin) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender.hasPermission("lukevanilla.reload")) {
            sender.sendMessage("플러그인을 재시작합니다.")
            Bukkit.getScheduler().runTask(plugin, Runnable {
                Bukkit.getPluginManager().disablePlugin(plugin)
                Bukkit.getPluginManager().enablePlugin(plugin)
                sender.sendMessage("플러그인이 재시작되었습니다.")
            })
            return true
        } else {
            sender.sendMessage("권한이 없습니다.")
            return true
        }
    }
}