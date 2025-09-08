package io.yodaka.eagle.map

import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration

data class MapConfig(
    val id: String,
    val name: String,
    val worldName: String,
    val timeLimit: Int,
    val spawnPoints: Map<String, List<SpawnPoint>>,
    val objectives: List<Objective>
) {
    companion object {
        fun fromYaml(config: YamlConfiguration): MapConfig {
            val id = config.getString("id") ?: throw IllegalArgumentException("Map ID is required")
            val name = config.getString("name") ?: id
            val worldName = config.getString("world-name") ?: id
            val timeLimit = config.getInt("time-limit", 1800)
            
            // スポーンポイントの読み込み
            val spawnPoints = mutableMapOf<String, List<SpawnPoint>>()
            val spawnSection = config.getConfigurationSection("spawn-points")
            spawnSection?.getKeys(false)?.forEach { teamId ->
                val teamSpawns = mutableListOf<SpawnPoint>()
                val teamSection = spawnSection.getConfigurationSection(teamId)
                teamSection?.getKeys(false)?.forEach { index ->
                    val spawnSection = teamSection.getConfigurationSection(index)
                    if (spawnSection != null) {
                        val spawn = SpawnPoint(
                            x = spawnSection.getDouble("x"),
                            y = spawnSection.getDouble("y"),
                            z = spawnSection.getDouble("z"),
                            yaw = spawnSection.getDouble("yaw", 0.0).toFloat(),
                            pitch = spawnSection.getDouble("pitch", 0.0).toFloat()
                        )
                        teamSpawns.add(spawn)
                    }
                }
                spawnPoints[teamId] = teamSpawns
            }
            
            // 勝利条件の読み込み
            val objectives = mutableListOf<Objective>()
            val objectivesSection = config.getConfigurationSection("objectives")
            objectivesSection?.getKeys(false)?.forEach { index ->
                val objSection = objectivesSection.getConfigurationSection(index)
                if (objSection != null) {
                    val type = objSection.getString("type") ?: return@forEach
                    val objective = when (type) {
                        "capture_flag" -> {
                            CaptureFlag(
                                team = objSection.getString("team") ?: "red",
                                location = LocationData(
                                    x = objSection.getDouble("location.x"),
                                    y = objSection.getDouble("location.y"),
                                    z = objSection.getDouble("location.z")
                                )
                            )
                        }
                        "kill_count" -> {
                            KillCount(target = objSection.getInt("target", 50))
                        }
                        "control_point" -> {
                            ControlPoint(
                                location = LocationData(
                                    x = objSection.getDouble("location.x"),
                                    y = objSection.getDouble("location.y"),
                                    z = objSection.getDouble("location.z")
                                ),
                                radius = objSection.getDouble("radius", 5.0)
                            )
                        }
                        else -> null
                    }
                    objective?.let { objectives.add(it) }
                }
            }
            
            return MapConfig(id, name, worldName, timeLimit, spawnPoints, objectives)
        }
    }
    
    fun getSpawnPoint(teamId: String, index: Int = 0): SpawnPoint? {
        val teamSpawns = spawnPoints[teamId] ?: return null
        return if (teamSpawns.isNotEmpty()) {
            teamSpawns[index % teamSpawns.size]
        } else null
    }
    
    fun getRandomSpawnPoint(teamId: String): SpawnPoint? {
        val teamSpawns = spawnPoints[teamId] ?: return null
        return if (teamSpawns.isNotEmpty()) {
            teamSpawns.random()
        } else null
    }
}

data class SpawnPoint(
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f
) {
    fun toLocation(world: org.bukkit.World): Location {
        return Location(world, x, y, z, yaw, pitch)
    }
}

data class LocationData(
    val x: Double,
    val y: Double,
    val z: Double
) {
    fun toLocation(world: org.bukkit.World): Location {
        return Location(world, x, y, z)
    }
}

sealed class Objective

data class CaptureFlag(
    val team: String,
    val location: LocationData
) : Objective()

data class KillCount(
    val target: Int
) : Objective()

data class ControlPoint(
    val location: LocationData,
    val radius: Double
) : Objective() 