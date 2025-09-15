package io.yodaka.eagle.protection

import io.yodaka.eagle.EaglePlugin
import io.yodaka.eagle.player.PlayerState
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerPickupItemEvent

class GameProtectionManager(private val plugin: EaglePlugin) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player

        // 待機中プレイヤーはブロックを破壊できない
        if (plugin.playerStateManager.isPlayerWaiting(player)) {
            event.isCancelled = true
            return
        }

        // 観戦中プレイヤーはブロックを破壊できない
        if (plugin.playerStateManager.isPlayerSpectating(player)) {
            event.isCancelled = true
            return
        }

        // ゲーム中プレイヤーの場合、境界チェック
        if (plugin.playerStateManager.isPlayerInGame(player)) {
            val currentMapId = plugin.mapManager.getCurrentMap()
            if (currentMapId != null) {
                val mapConfig = plugin.mapManager.loadMap(currentMapId)
                if (mapConfig != null && !mapConfig.isInGameBoundary(event.block.location)) {
                    event.isCancelled = true
                    player.sendMessage("ゲーム境界外ではブロックを破壊できません")
                    return
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player

        // 待機中プレイヤーはブロックを設置できない
        if (plugin.playerStateManager.isPlayerWaiting(player)) {
            event.isCancelled = true
            return
        }

        // 観戦中プレイヤーはブロックを設置できない
        if (plugin.playerStateManager.isPlayerSpectating(player)) {
            event.isCancelled = true
            return
        }

        // ゲーム中プレイヤーの場合、境界チェック
        if (plugin.playerStateManager.isPlayerInGame(player)) {
            val currentMapId = plugin.mapManager.getCurrentMap()
            if (currentMapId != null) {
                val mapConfig = plugin.mapManager.loadMap(currentMapId)
                if (mapConfig != null && !mapConfig.isInGameBoundary(event.block.location)) {
                    event.isCancelled = true
                    player.sendMessage("ゲーム境界外ではブロックを設置できません")
                    return
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player

        // 待機中・観戦中プレイヤーは特定のアイテムの使用を制限
        if (plugin.playerStateManager.isPlayerWaiting(player) ||
            plugin.playerStateManager.isPlayerSpectating(player)) {

            val item = event.item
            if (item != null && isRestrictedItem(item.type)) {
                event.isCancelled = true
                return
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity
        if (entity !is Player) return

        val player = entity as Player

        // 待機中・観戦中プレイヤーはダメージを受けない
        if (plugin.playerStateManager.isPlayerWaiting(player) ||
            plugin.playerStateManager.isPlayerSpectating(player)) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val victim = event.entity
        val damager = event.damager

        // 被害者がプレイヤーの場合
        if (victim is Player) {
            // 待機中・観戦中プレイヤーはダメージを受けない
            if (plugin.playerStateManager.isPlayerWaiting(victim) ||
                plugin.playerStateManager.isPlayerSpectating(victim)) {
                event.isCancelled = true
                return
            }
        }

        // 加害者の処理
        var attackerPlayer: Player? = null

        when (damager) {
            is Player -> attackerPlayer = damager
            is Projectile -> {
                val shooter = damager.shooter
                if (shooter is Player) {
                    attackerPlayer = shooter
                }
            }
        }

        // 待機中・観戦中プレイヤーは攻撃できない
        if (attackerPlayer != null &&
            (plugin.playerStateManager.isPlayerWaiting(attackerPlayer) ||
             plugin.playerStateManager.isPlayerSpectating(attackerPlayer))) {
            event.isCancelled = true
            return
        }

        // ゲーム中でない場合はPvP無効
        if (attackerPlayer != null && victim is Player) {
            if (!plugin.playerStateManager.isPlayerInGame(attackerPlayer) ||
                !plugin.playerStateManager.isPlayerInGame(victim)) {
                event.isCancelled = true
                return
            }

            // 同チームの攻撃は無効（フレンドリーファイア無効）
            val attackerTeam = plugin.teamManager.getPlayerTeam(attackerPlayer)
            val victimTeam = plugin.teamManager.getPlayerTeam(victim)

            if (attackerTeam != null && victimTeam != null && attackerTeam.id == victimTeam.id) {
                event.isCancelled = true
                attackerPlayer.sendMessage("チームメンバーは攻撃できません")
                return
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        val entity = event.entity
        if (entity !is Player) return

        val player = entity as Player

        // 待機中・観戦中プレイヤーの満腹度は変化しない
        if (plugin.playerStateManager.isPlayerWaiting(player) ||
            plugin.playerStateManager.isPlayerSpectating(player)) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player

        // 待機中・観戦中プレイヤーはアイテムをドロップできない
        if (plugin.playerStateManager.isPlayerWaiting(player) ||
            plugin.playerStateManager.isPlayerSpectating(player)) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked
        if (player !is Player) return

        // 待機中・観戦中プレイヤーのインベントリ操作を制限
        if (plugin.playerStateManager.isPlayerWaiting(player) ||
            plugin.playerStateManager.isPlayerSpectating(player)) {
            event.isCancelled = true
            return
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    @Suppress("DEPRECATION")
    fun onPlayerPickupItem(event: PlayerPickupItemEvent) {
        val player = event.player

        // 待機中・観戦中プレイヤーはアイテムを拾えない
        if (plugin.playerStateManager.isPlayerWaiting(player) ||
            plugin.playerStateManager.isPlayerSpectating(player)) {
            event.isCancelled = true
            return
        }
    }

    /**
     * 制限されているアイテムかチェック
     */
    private fun isRestrictedItem(material: Material): Boolean {
        return when (material) {
            Material.TNT,
            Material.LAVA_BUCKET,
            Material.FIRE_CHARGE,
            Material.FLINT_AND_STEEL,
            Material.BOW,
            Material.CROSSBOW,
            Material.TRIDENT -> true
            else -> {
                // 武器・道具類は制限
                material.name.contains("SWORD") ||
                material.name.contains("AXE") ||
                material.name.contains("PICKAXE") ||
                material.name.contains("SHOVEL") ||
                material.name.contains("HOE")
            }
        }
    }
}