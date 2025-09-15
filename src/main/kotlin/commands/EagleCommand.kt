package io.yodaka.eagle.commands

import io.yodaka.eagle.EaglePlugin
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class EagleCommand(private val plugin: EaglePlugin) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "join" -> handleJoin(sender)
            "leave" -> handleLeave(sender)
            "stats" -> handleStats(sender, args)
            "forcestart" -> handleForceStart(sender)
            "forceend" -> handleForceEnd(sender)
            "reload" -> handleReload(sender)
            "map" -> handleMap(sender, args)
            "team" -> handleTeam(sender, args)
            "help" -> showHelp(sender)
            else -> {
                plugin.messageManager.sendUnknownCommandMessage(sender as? Player ?: return false)
            }
        }
        
        return true
    }
    
    private fun handleJoin(sender: CommandSender) {
        if (sender !is Player) {
            plugin.messageManager.sendPlayerOnlyMessage(sender as Player)
            return
        }

        // ゲーム中の場合は参加できない
        if (plugin.gameManager.isPlayerInGame(sender)) {
            sender.sendMessage("§c既にゲームに参加しています。")
            return
        }

        // 既にロビーにいる場合
        if (plugin.lobbyManager.isInLobby(sender)) {
            sender.sendMessage("§e既にロビーに参加しています。")
            return
        }

        // ロビーに追加（テレポートはスキップ - プレイヤーは既に待機エリアにいる）
        plugin.lobbyManager.addPlayerToLobby(sender, skipTeleport = true)
        sender.sendMessage("§aゲーム待機に参加しました！最低${plugin.lobbyManager.getMinPlayers()}人でゲームが開始されます。")
    }
    
    private fun handleLeave(sender: CommandSender) {
        if (sender !is Player) {
            plugin.messageManager.sendPlayerOnlyMessage(sender as Player)
            return
        }
        
        // ロビーから退出
        if (plugin.lobbyManager.isInLobby(sender)) {
            plugin.lobbyManager.removePlayerFromLobby(sender)
            sender.sendMessage("§eロビーから退出しました。")
            return
        }
        
        // ゲームから退出
        if (plugin.gameManager.isPlayerInGame(sender)) {
            plugin.teamManager.removePlayerFromTeam(sender)

            // 待機状態に戻す（テレポートは不要、既に同じマップにいる）
            plugin.playerStateManager.setPlayerWaiting(sender)
            sender.sendMessage("§eゲームから退出しました。観戦モードに戻りました。")
            return
        }
        
        sender.sendMessage("§cロビーまたはゲームに参加していません。")
    }
    
    private fun handleStats(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            plugin.messageManager.sendPlayerOnlyMessage(sender as Player)
            return
        }
        
        val targetPlayer = if (args.size > 1) {
            plugin.server.getPlayer(args[1])
        } else {
            sender
        }
        
        if (targetPlayer == null) {
            sender.sendMessage("§cプレイヤーが見つかりません: ${args[1]}")
            return
        }
        
        val stats = plugin.scoreManager.getPlayerStats(targetPlayer)
        sender.sendMessage("§8========== §a${targetPlayer.name}の統計 §8==========")
        sender.sendMessage("§eキル: §f${stats.kills}")
        sender.sendMessage("§cデス: §f${stats.deaths}")
        sender.sendMessage("§bアシスト: §f${stats.assists}")
        sender.sendMessage("§dポイント: §f${stats.points}")
        sender.sendMessage("§6K/D比: §f${"%.2f".format(stats.getKDRatio())}")
        sender.sendMessage("§8================================")
    }
    
    private fun handleForceStart(sender: CommandSender) {
        if (!sender.hasPermission("eagle.admin")) {
            plugin.messageManager.sendNoPermissionMessage(sender as? Player ?: return)
            return
        }
        
        plugin.lobbyManager.forceStartGame()
        sender.sendMessage("§aゲームを強制開始しました。")
    }
    
    private fun handleForceEnd(sender: CommandSender) {
        if (!sender.hasPermission("eagle.admin")) {
            plugin.messageManager.sendNoPermissionMessage(sender as? Player ?: return)
            return
        }
        
        plugin.gameManager.forceEndGame()
        sender.sendMessage("§eゲームを強制終了しました。")
    }
    
    private fun handleReload(sender: CommandSender) {
        if (!sender.hasPermission("eagle.admin")) {
            plugin.messageManager.sendNoPermissionMessage(sender as? Player ?: return)
            return
        }
        
        plugin.reloadConfig()
        sender.sendMessage("§a設定ファイルを再読み込みしました。")
    }
    
    private fun handleMap(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("eagle.admin")) {
            plugin.messageManager.sendNoPermissionMessage(sender as? Player ?: return)
            return
        }
        
        if (args.size < 2) {
            sender.sendMessage("§c使用法: /eagle map <list|set|next> [マップ名]")
            return
        }
        
        when (args[1].lowercase()) {
            "list" -> {
                val rotation = plugin.mapManager.getMapRotation()
                val current = plugin.mapManager.getCurrentMap()
                sender.sendMessage("§8========== §eマップローテーション §8==========")
                rotation.forEachIndexed { index, mapId ->
                    val marker = if (mapId == current) "§a▶ " else "§7  "
                    sender.sendMessage("$marker§f$mapId")
                }
                sender.sendMessage("§8================================")
            }
            "set" -> {
                if (args.size < 3) {
                    sender.sendMessage("§c使用法: /eagle map set <マップ名>")
                    return
                }
                
                val mapId = args[2]
                if (plugin.mapManager.setCurrentMap(mapId)) {
                    sender.sendMessage("§a現在のマップを「$mapId」に設定しました。")
                } else {
                    sender.sendMessage("§cマップ「$mapId」が見つかりません。")
                }
            }
            "next" -> {
                val nextMap = plugin.mapManager.rotateToNextMap()
                if (nextMap != null) {
                    sender.sendMessage("§a次のマップ「$nextMap」に移行しました。")
                } else {
                    sender.sendMessage("§c利用可能なマップがありません。")
                }
            }
            else -> {
                sender.sendMessage("§c使用法: /eagle map <list|set|next> [マップ名]")
            }
        }
    }
    
    private fun handleTeam(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) {
            plugin.messageManager.sendPlayerOnlyMessage(sender as Player)
            return
        }
        
        if (args.size < 2) {
            // 現在のチーム情報を表示
            val team = plugin.teamManager.getPlayerTeam(sender)
            if (team != null) {
                sender.sendMessage("§aあなたは${team.name}に所属しています。")
                sender.sendMessage("§eチームメンバー: ${team.getOnlineMembers().joinToString(", ") { it.name }}")
                sender.sendMessage("§dチームスコア: ${team.score}")
            } else {
                sender.sendMessage("§cチームに所属していません。")
            }
            return
        }
        
        when (args[1].lowercase()) {
            "list" -> {
                val teams = plugin.teamManager.getAllTeams()
                sender.sendMessage("§8========== §eチーム一覧 §8==========")
                teams.forEach { team ->
                    val members = team.getOnlineMembers()
                    sender.sendMessage("§f${team.name} §7(${members.size}/${team.maxSize})")
                    if (members.isNotEmpty()) {
                        sender.sendMessage("§7  メンバー: ${members.joinToString(", ") { it.name }}")
                    }
                }
                sender.sendMessage("§8================================")
            }
            "join" -> {
                if (args.size < 3) {
                    sender.sendMessage("§c使用法: /eagle team join <チーム名>")
                    return
                }
                
                val teamId = args[2].lowercase()
                if (plugin.teamManager.assignPlayerToTeam(sender, teamId)) {
                    sender.sendMessage("§a${teamId}チームに参加しました！")
                } else {
                    sender.sendMessage("§cチームへの参加に失敗しました。チームが満員か存在しません。")
                }
            }
            "leave" -> {
                plugin.teamManager.removePlayerFromTeam(sender)
                sender.sendMessage("§eチームから退出しました。")
            }
            else -> {
                sender.sendMessage("§c使用法: /eagle team <list|join|leave> [チーム名]")
            }
        }
    }
    
    private fun showHelp(sender: CommandSender) {
        sender.sendMessage(plugin.messageManager.getMessage("commands.help-header"))
        sender.sendMessage("§a/eagle join §7- ゲームに参加")
        sender.sendMessage("§c/eagle leave §7- ゲームから退出")
        sender.sendMessage("§e/eagle stats [プレイヤー] §7- 統計を表示")
        sender.sendMessage("§b/eagle team <list|join|leave> §7- チーム管理")
        
        if (sender.hasPermission("eagle.admin")) {
            sender.sendMessage("§8========== §c管理者コマンド §8==========")
            sender.sendMessage("§c/eagle forcestart §7- ゲームを強制開始")
            sender.sendMessage("§c/eagle forceend §7- ゲームを強制終了")
            sender.sendMessage("§c/eagle reload §7- 設定を再読み込み")
            sender.sendMessage("§c/eagle map <list|set|next> §7- マップ管理")
        }
        
        sender.sendMessage(plugin.messageManager.getMessage("commands.help-footer"))
    }
    
    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (args.size == 1) {
            val subcommands = mutableListOf("join", "leave", "stats", "team", "help")
            if (sender.hasPermission("eagle.admin")) {
                subcommands.addAll(listOf("forcestart", "forceend", "reload", "map"))
            }
            return subcommands.filter { it.startsWith(args[0].lowercase()) }
        }
        
        if (args.size == 2) {
            when (args[0].lowercase()) {
                "stats" -> {
                    return plugin.server.onlinePlayers.map { it.name }
                        .filter { it.startsWith(args[1], true) }
                }
                "map" -> {
                    if (sender.hasPermission("eagle.admin")) {
                        return listOf("list", "set", "next")
                            .filter { it.startsWith(args[1].lowercase()) }
                    }
                }
                "team" -> {
                    return listOf("list", "join", "leave")
                        .filter { it.startsWith(args[1].lowercase()) }
                }
            }
        }
        
        if (args.size == 3) {
            when (args[0].lowercase()) {
                "map" -> {
                    if (args[1].lowercase() == "set" && sender.hasPermission("eagle.admin")) {
                        return plugin.mapManager.getMapRotation()
                            .filter { it.startsWith(args[2], true) }
                    }
                }
                "team" -> {
                    if (args[1].lowercase() == "join") {
                        return plugin.teamManager.getAllTeams().map { it.id }
                            .filter { it.startsWith(args[2], true) }
                    }
                }
            }
        }
        
        return emptyList()
    }
} 