package com.example

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.AshGray
import com.example.ui.theme.DarkGround
import com.example.ui.theme.DriedBlood
import com.example.ui.theme.DullSteel
import com.example.ui.theme.FadedOlive
import com.example.ui.theme.MatteBlack
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RustRed
import com.example.ui.theme.ScrapBrown
import com.example.ui.theme.TerminalText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

val ToxicGreen = Color(0xFF6B8E23)
val StunYellow = Color(0xFFB8860B)
val CyanGlow = Color(0xFF00CED1)

// --- MODELS ---
enum class GameState { MAIN_MENU, MAP, COMBAT, CAMP, REWARD, GAME_OVER, CAMPAIGN_VICTORY, EVENT }
enum class NodeType { COMBAT, SCAVENGE, CAMP, BOSS, EVENT }

data class EventState(
    val title: String = "Wasteland Anomaly",
    val description: String = "You discover a rusted medical transport rigged with tripwires.",
    val resolved: Boolean = false,
    val outcome: String = ""
)

data class MapNode(
    val id: String,
    val type: NodeType,
    val tier: Int,
    val x: Float,
    val nextNodes: List<String> = emptyList()
)

enum class IntentType { ATTACK, DEBUFF, BUFF, AOE_ATTACK }
data class EnemyIntent(val type: IntentType, val value: Int = 0, val targetId: String? = null, val desc: String)

enum class EntityType { BRUISER, MEDIC, SCAVENGER, MUTANT, RAIDER, DRONE, BOSS }
enum class EffectType { RADIATION, STUN, ADRENALINE }
data class StatusEffect(val type: EffectType, val duration: Int)

data class Entity(
    val id: String,
    val name: String,
    val hp: Int,
    val maxHp: Int,
    val speed: Int,
    val armor: Int = 0,
    val damageBonus: Int = 0,
    val isPlayer: Boolean,
    val rank: Int,
    val entityType: EntityType,
    val statusEffects: List<StatusEffect> = emptyList(),
    val isDead: Boolean = false,
    val intent: EnemyIntent? = null
)

enum class TurnPhase { PLAYER_ACTION, PLAYER_TARGET_ENEMY, PLAYER_TARGET_ALLY, PLAYER_TARGET_REPOSITION, ENEMY_TURN }
enum class PlayerAction { HEAVY_WRENCH, IRON_GUARD, CAUTERIZE, RAD_SHOT, PIPE_RIFLE, FLASHBANG, BATTERING_RAM, TACTICAL_RETREAT, REPOSITION, NONE }

fun isValidTarget(action: PlayerAction, targetRank: Int): Boolean {
    return when (action) {
        PlayerAction.HEAVY_WRENCH -> targetRank in 1..2
        PlayerAction.BATTERING_RAM -> targetRank in 1..2
        PlayerAction.RAD_SHOT -> targetRank in 2..3
        PlayerAction.PIPE_RIFLE -> targetRank in 2..3
        PlayerAction.FLASHBANG -> targetRank in 1..2
        else -> true
    }
}

fun canCast(action: PlayerAction, casterRank: Int): Boolean {
    return when (action) {
        PlayerAction.HEAVY_WRENCH -> casterRank in 1..2
        PlayerAction.BATTERING_RAM -> casterRank in 1..2
        PlayerAction.CAUTERIZE -> casterRank in 2..3
        PlayerAction.RAD_SHOT -> casterRank in 2..3
        PlayerAction.PIPE_RIFLE -> casterRank == 3
        PlayerAction.FLASHBANG -> casterRank in 2..3
        PlayerAction.TACTICAL_RETREAT -> casterRank in 1..2
        else -> true
    }
}

data class FloatingText(val id: String, val entityId: String, val text: String, val color: Color)

enum class Relic(val title: String, val desc: String) {
    SPIKED_KNUCKLES("Spiked Knuckles", "+15% physical damage"),
    LEAD_LINING("Lead Lining", "50% less Radiation damage"),
    SCRAP_MAGNET("Scrap Magnet", "+30% scrap found"),
    ADRENALINE_INJECTOR("Adrenaline Injector", "+50% speed for 2 turns when <25% HP")
}

enum class HapticType { NONE, LIGHT, HEAVY, PULSE }
data class HapticSignal(val type: HapticType, val id: Long = 0L)

data class CombatState(
    val wave: Int = 1,
    val entities: List<Entity> = emptyList(),
    val currentTurnIndex: Int = -1,
    val activeEntityId: String? = null,
    val phase: TurnPhase = TurnPhase.PLAYER_ACTION,
    val log: List<String> = emptyList(),
    val selectedAction: PlayerAction = PlayerAction.NONE,
    val floatingTexts: List<FloatingText> = emptyList(),
    val shakeTrigger: Long = 0L,
    val bossTurnCount: Int = 0
)

data class MetaState(
    val runsAttempted: Int = 0,
    val bossesDefeated: Int = 0,
    val intel: Int = 0,
    val bonusScrapLevel: Int = 0,
    val bonusHpLevel: Int = 0
)

data class CampaignState(
    val gameState: GameState = GameState.MAIN_MENU,
    val roster: List<Entity> = emptyList(),
    val scrap: Int = 0,
    val totalScrapCollected: Int = 0,
    val turnsTaken: Int = 0,
    val mapNodes: List<MapNode> = emptyList(),
    val currentTier: Int = 0,
    val currentNodeId: String? = null,
    val visitedNodes: Set<String> = emptySet(),
    val rewardScrap: Int = 0,
    val rewardRelic: Relic? = null,
    val relics: Set<Relic> = emptySet(),
    val combatState: CombatState = CombatState(),
    val eventState: EventState? = null,
    val hapticSignal: HapticSignal = HapticSignal(HapticType.NONE)
)

// --- VIEWMODEL ---
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("citadel_meta", Context.MODE_PRIVATE)
    private val savePrefs = application.getSharedPreferences("citadel_save", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    private val _hasSavedGame = MutableStateFlow(savePrefs.contains("campaign_data"))
    val hasSavedGame: StateFlow<Boolean> = _hasSavedGame.asStateFlow()

    
    private val _metaState = MutableStateFlow(
        MetaState(
            runsAttempted = prefs.getInt("runs", 0),
            bossesDefeated = prefs.getInt("bosses", 0),
            intel = prefs.getInt("intel", 0),
            bonusScrapLevel = prefs.getInt("bonusScrap", 0),
            bonusHpLevel = prefs.getInt("bonusHp", 0)
        )
    )
    val metaState: StateFlow<MetaState> = _metaState.asStateFlow()

    private val _campaignState = MutableStateFlow(CampaignState())
    val campaignState: StateFlow<CampaignState> = _campaignState.asStateFlow()

    private fun saveMetaState(state: MetaState) {
        prefs.edit().apply {
            putInt("runs", state.runsAttempted)
            putInt("bosses", state.bossesDefeated)
            putInt("intel", state.intel)
            putInt("bonusScrap", state.bonusScrapLevel)
            putInt("bonusHp", state.bonusHpLevel)
            apply()
        }
        _metaState.value = state
    }

    private fun saveGame() {
        if (_campaignState.value.gameState == GameState.MAIN_MENU || _campaignState.value.gameState == GameState.GAME_OVER) return
        val json = gson.toJson(_campaignState.value)
        savePrefs.edit().putString("campaign_data", json).apply()
        _hasSavedGame.value = true
    }

    fun loadGame() {
        val json = savePrefs.getString("campaign_data", null)
        if (json != null) {
            try {
                val state = gson.fromJson(json, CampaignState::class.java)
                _campaignState.value = state
            } catch (e: Exception) {
                deleteSaveGame()
            }
        }
    }

    private fun deleteSaveGame() {
        savePrefs.edit().remove("campaign_data").apply()
        _hasSavedGame.value = false
    }

    fun upgradeScrap() {
        val s = _metaState.value
        if (s.intel >= 10) saveMetaState(s.copy(intel = s.intel - 10, bonusScrapLevel = s.bonusScrapLevel + 1))
    }

    fun upgradeHp() {
        val s = _metaState.value
        if (s.intel >= 10) saveMetaState(s.copy(intel = s.intel - 10, bonusHpLevel = s.bonusHpLevel + 1))
    }

    private fun grantIntel(amount: Int, bossDefeated: Boolean = false) {
        val s = _metaState.value
        saveMetaState(s.copy(
            intel = s.intel + amount,
            bossesDefeated = if (bossDefeated) s.bossesDefeated + 1 else s.bossesDefeated
        ))
    }

    fun triggerHaptic(type: HapticType) {
        _campaignState.update { it.copy(hapticSignal = HapticSignal(type, System.currentTimeMillis())) }
    }

    private fun generateMap(): List<MapNode> {
        return listOf(
            MapNode("1_1", NodeType.COMBAT, 1, 0.25f, listOf("2_1", "2_2")),
            MapNode("1_2", NodeType.SCAVENGE, 1, 0.5f, listOf("2_2")),
            MapNode("1_3", NodeType.COMBAT, 1, 0.75f, listOf("2_2", "2_3")),
            MapNode("2_1", NodeType.COMBAT, 2, 0.3f, listOf("3_1", "3_2")),
            MapNode("2_2", NodeType.CAMP, 2, 0.5f, listOf("3_2")),
            MapNode("2_3", NodeType.COMBAT, 2, 0.7f, listOf("3_2", "3_3")),
            MapNode("3_1", NodeType.EVENT, 3, 0.25f, listOf("4_1")),
            MapNode("3_2", NodeType.COMBAT, 3, 0.5f, listOf("4_1")),
            MapNode("3_3", NodeType.CAMP, 3, 0.75f, listOf("4_1")),
            MapNode("4_1", NodeType.BOSS, 4, 0.5f, emptyList())
        )
    }

    fun startNewRun() {
        val meta = _metaState.value
        saveMetaState(meta.copy(runsAttempted = meta.runsAttempted + 1))

        val hpBonus = meta.bonusHpLevel * 5
        val startScrap = meta.bonusScrapLevel * 10

        val roster = listOf(
            Entity("p1", "Scrap Bruiser", 80 + hpBonus, 80 + hpBonus, speed = 8, isPlayer = true, rank = 1, entityType = EntityType.BRUISER),
            Entity("p2", "Wastes Medic", 50 + hpBonus, 50 + hpBonus, speed = 12, isPlayer = true, rank = 2, entityType = EntityType.MEDIC),
            Entity("p3", "Scavenger", 45 + hpBonus, 45 + hpBonus, speed = 15, isPlayer = true, rank = 3, entityType = EntityType.SCAVENGER)
        )
        
        _campaignState.value = CampaignState(
            gameState = GameState.MAP,
            roster = roster,
            scrap = startScrap,
            totalScrapCollected = startScrap,
            turnsTaken = 0,
            mapNodes = generateMap(),
            currentTier = 0,
            currentNodeId = null,
            visitedNodes = emptySet(),
            relics = emptySet()
        )
        saveGame()
    }

    fun returnToCitadel() {
        _campaignState.update { it.copy(gameState = GameState.MAIN_MENU) }
    }

    fun selectMapNode(nodeId: String) {
        val s = _campaignState.value
        val node = s.mapNodes.find { it.id == nodeId } ?: return
        
        val isValid = if (s.currentTier == 0) {
            node.tier == 1
        } else {
            val currentNode = s.mapNodes.find { it.id == s.currentNodeId }
            currentNode?.nextNodes?.contains(nodeId) == true
        }
        if (!isValid) return

        triggerHaptic(HapticType.LIGHT)
        val newVisited = s.visitedNodes + nodeId
        _campaignState.update { it.copy(currentNodeId = nodeId, currentTier = node.tier, visitedNodes = newVisited) }

        when (node.type) {
            NodeType.COMBAT -> startCombat(isBoss = false)
            NodeType.BOSS -> startCombat(isBoss = true)
            NodeType.SCAVENGE -> {
                val hasMagnet = s.relics.contains(Relic.SCRAP_MAGNET)
                val baseScrap = (30..60).random()
                val finalScrap = if (hasMagnet) (baseScrap * 1.3f).toInt() else baseScrap
                
                val unownedRelics = Relic.values().filter { !s.relics.contains(it) }
                val droppedRelic = unownedRelics.randomOrNull()

                _campaignState.update { it.copy(
                    gameState = GameState.REWARD,
                    rewardScrap = finalScrap,
                    rewardRelic = droppedRelic
                )}
            }
            NodeType.CAMP -> _campaignState.update { it.copy(gameState = GameState.CAMP) }
            NodeType.EVENT -> {
                _campaignState.update { it.copy(
                    gameState = GameState.EVENT,
                    eventState = EventState()
                )}
            }
        }
        saveGame()
    }

    private fun startCombat(isBoss: Boolean) {
        val s = _campaignState.value
        val waveNum = s.currentTier
        val hpMult = 1f + (waveNum - 1) * 0.4f
        
        val enemies = if (isBoss) {
            listOf(Entity("b1", "Warlord", (400 * hpMult).toInt(), (400 * hpMult).toInt(), speed = 9, isPlayer = false, rank = 2, entityType = EntityType.BOSS, armor = 15))
        } else {
            listOf(
                Entity("e1", "Mutant", (70 * hpMult).toInt(), (70 * hpMult).toInt(), speed = 7, isPlayer = false, rank = 1, entityType = EntityType.MUTANT),
                Entity("e2", "Raider", (40 * hpMult).toInt(), (40 * hpMult).toInt(), speed = 10, isPlayer = false, rank = 2, entityType = EntityType.RAIDER),
                Entity("e3", "Scrap Drone", (25 * hpMult).toInt(), (25 * hpMult).toInt(), speed = 18, isPlayer = false, rank = 3, entityType = EntityType.DRONE)
            )
        }
        
        val combatEntities = (s.roster + enemies).sortedByDescending { it.speed }
        
        _campaignState.update { it.copy(
            gameState = GameState.COMBAT,
            combatState = CombatState(wave = waveNum, entities = combatEntities, log = listOf("COMBAT INITIATED."), currentTurnIndex = -1, bossTurnCount = 0)
        )}
        generateIntents()
        advanceTurn()
    }
    
    private fun generateIntents() {
        val s = _campaignState.value
        val c = s.combatState
        val wave = c.wave
        
        val players = c.entities.filter { it.isPlayer && !it.isDead }
        if (players.isEmpty()) return

        val newEntities = c.entities.map { enemy ->
            if (enemy.isPlayer || enemy.isDead || enemy.intent != null) return@map enemy
            
            var chosenType = IntentType.ATTACK
            var chosenValue = 0
            var chosenTargetId: String? = players.random().id
            var desc = ""
            
            // Randomly stop attacking? The prompt asked for: "Enemies must stop attacking at random."
            if (kotlin.random.Random.nextFloat() < 0.15f) {
                chosenType = IntentType.BUFF
                desc = "🛡️ Wait"
                chosenTargetId = enemy.id
            } else {
                when (enemy.entityType) {
                    EntityType.MUTANT -> {
                        val unradiated = players.filter { p -> p.statusEffects.none { it.type == EffectType.RADIATION } }
                        if (unradiated.isNotEmpty() && kotlin.random.Random.nextBoolean()) {
                            chosenTargetId = unradiated.random().id
                            chosenType = IntentType.DEBUFF
                            desc = "☢️ Toxic"
                        } else {
                            chosenType = IntentType.ATTACK
                            chosenValue = (20..35).random() + enemy.damageBonus
                            chosenValue = (chosenValue * (1f + (wave - 1) * 0.2f)).toInt()
                            val lethalTarget = players.find { it.hp <= chosenValue }
                            chosenTargetId = lethalTarget?.id ?: players.minByOrNull { it.hp }!!.id
                            desc = "⚔️ $chosenValue"
                        }
                    }
                    EntityType.RAIDER -> {
                        chosenType = IntentType.ATTACK
                        chosenValue = (15..25).random() + enemy.damageBonus
                        chosenValue = (chosenValue * (1f + (wave - 1) * 0.2f)).toInt()
                        val lethalTarget = players.find { it.hp <= chosenValue }
                        chosenTargetId = lethalTarget?.id ?: players.minByOrNull { it.hp }!!.id
                        desc = "⚔️ $chosenValue"
                    }
                    EntityType.DRONE -> {
                        if (kotlin.random.Random.nextFloat() < 0.4f) {
                            chosenType = IntentType.BUFF
                            chosenTargetId = enemy.id
                            desc = "🛡️ Armor"
                        } else {
                            chosenType = IntentType.ATTACK
                            chosenValue = (5..15).random() + enemy.damageBonus
                            chosenValue = (chosenValue * (1f + (wave - 1) * 0.2f)).toInt()
                            val lethalTarget = players.find { it.hp <= chosenValue }
                            chosenTargetId = lethalTarget?.id ?: players.minByOrNull { it.hp }!!.id
                            desc = "⚔️ $chosenValue"
                        }
                    }
                    EntityType.BOSS -> {
                        val rand = kotlin.random.Random.nextFloat()
                        if (rand < 0.25f) {
                            chosenType = IntentType.DEBUFF
                            desc = "⚡ Stun"
                            chosenTargetId = players.random().id
                        } else if (rand < 0.5f) {
                            chosenType = IntentType.AOE_ATTACK
                            chosenValue = (20..30).random() + enemy.damageBonus
                            chosenValue = (chosenValue * (1f + (wave - 1) * 0.2f)).toInt()
                            desc = "💢 $chosenValue"
                            chosenTargetId = null
                        } else {
                            chosenType = IntentType.ATTACK
                            chosenValue = (40..60).random() + enemy.damageBonus
                            chosenValue = (chosenValue * (1f + (wave - 1) * 0.2f)).toInt()
                            val lethalTarget = players.find { it.hp <= chosenValue }
                            chosenTargetId = lethalTarget?.id ?: players.minByOrNull { it.hp }!!.id
                            desc = "⚔️ $chosenValue"
                        }
                    }
                    else -> {
                        chosenType = IntentType.ATTACK
                        chosenValue = 10
                        desc = "⚔️ 10"
                    }
                }
            }
            
            enemy.copy(intent = EnemyIntent(chosenType, chosenValue, chosenTargetId, desc))
        }
        updateCombatState { it.copy(entities = newEntities) }
    }

    private fun updateCombatState(updater: (CombatState) -> CombatState) {
        _campaignState.update { it.copy(combatState = updater(it.combatState)) }
    }

    private fun log(message: String) {
        updateCombatState { it.copy(log = listOf(message) + it.log) }
    }

    private fun triggerShake() {
        updateCombatState { it.copy(shakeTrigger = System.currentTimeMillis()) }
    }

    private fun spawnFloatingText(entityId: String, text: String, color: Color) {
        val ft = FloatingText(UUID.randomUUID().toString(), entityId, text, color)
        updateCombatState { it.copy(floatingTexts = it.floatingTexts + ft) }
        viewModelScope.launch {
            delay(1000)
            updateCombatState { it.copy(floatingTexts = it.floatingTexts.filter { f -> f.id != ft.id }) }
        }
    }

    private fun checkCombatEnd(): Boolean {
        val s = _campaignState.value
        val c = s.combatState
        val playersAlive = c.entities.any { it.isPlayer && !it.isDead }
        val enemiesAlive = c.entities.any { !it.isPlayer && !it.isDead }

        if (!playersAlive) {
            grantIntel(s.currentTier * 2)
            _campaignState.update { it.copy(gameState = GameState.GAME_OVER) }
            deleteSaveGame()
            return true
        }
        if (!enemiesAlive) {
            val updatedRoster = c.entities.filter { it.isPlayer }.map { it.copy(armor = 0, statusEffects = emptyList()) }
            
            if (s.currentTier == 4) {
                grantIntel(20 + s.currentTier * 2, bossDefeated = true)
                _campaignState.update { it.copy(gameState = GameState.CAMPAIGN_VICTORY, roster = updatedRoster) }
                triggerHaptic(HapticType.PULSE)
                deleteSaveGame()
            } else {
                val baseScrap = (40..80).random() + (s.currentTier * 10)
                val finalScrap = if (s.relics.contains(Relic.SCRAP_MAGNET)) (baseScrap * 1.3f).toInt() else baseScrap
                var droppedRelic: Relic? = null
                if (s.currentTier == 3) droppedRelic = Relic.values().filter { !s.relics.contains(it) }.randomOrNull()

                _campaignState.update { it.copy(
                    gameState = GameState.REWARD, roster = updatedRoster, rewardScrap = finalScrap, rewardRelic = droppedRelic
                )}
                saveGame()
            }
            return true
        }
        return false
    }

    private fun advanceTurn() {
        if (checkCombatEnd()) return
        
        _campaignState.update { it.copy(turnsTaken = it.turnsTaken + 1) }

        val c = _campaignState.value.combatState
        val livingEntities = c.entities.filter { !it.isDead }
        if (livingEntities.isEmpty()) return

        var nextIndex = (c.currentTurnIndex + 1) % c.entities.size
        var steps = 0
        while (c.entities[nextIndex].isDead && steps < c.entities.size) {
            nextIndex = (nextIndex + 1) % c.entities.size
            steps++
        }

        val nextEntity = c.entities[nextIndex]
        updateCombatState { it.copy(currentTurnIndex = nextIndex, activeEntityId = nextEntity.id) }

        var isStunned = false
        val newEffects = nextEntity.statusEffects.mapNotNull { effect ->
            var remaining = effect.duration
            when (effect.type) {
                EffectType.RADIATION -> {
                    val hasLining = _campaignState.value.relics.contains(Relic.LEAD_LINING)
                    val radDmg = if (hasLining && nextEntity.isPlayer) 2 else 5
                    log("${nextEntity.name} suffers $radDmg Rad DMG!")
                    damageEntity(nextEntity.id, radDmg, isCrit = false, prefix = "RAD ")
                    remaining -= 1
                }
                EffectType.STUN -> {
                    log("${nextEntity.name} is STUNNED!")
                    spawnFloatingText(nextEntity.id, "STUNNED!", StunYellow)
                    isStunned = true
                    remaining -= 1
                }
                EffectType.ADRENALINE -> {
                    remaining -= 1
                }
            }
            if (remaining > 0) effect.copy(duration = remaining) else null
        }

        val tempEntities = c.entities.map { if (it.id == nextEntity.id) it.copy(armor = 0, statusEffects = newEffects) else it }
        val sortedEntities = tempEntities.sortedByDescending { ent ->
            val spd = ent.speed
            if (ent.statusEffects.any { it.type == EffectType.ADRENALINE }) (spd * 1.5f).toInt() else spd
        }
        val newActiveIndex = sortedEntities.indexOfFirst { it.id == nextEntity.id }.takeIf { it >= 0 } ?: c.currentTurnIndex

        updateCombatState { st -> st.copy(entities = sortedEntities, currentTurnIndex = newActiveIndex) }
        if (checkCombatEnd()) return

        val afterEffectsEnt = _campaignState.value.combatState.entities.find { it.id == nextEntity.id }
        if (afterEffectsEnt == null || afterEffectsEnt.isDead) {
            advanceTurn()
            return
        }

        if (isStunned) {
            viewModelScope.launch { delay(800); advanceTurn() }
            return
        }

        if (afterEffectsEnt.isPlayer) {
            updateCombatState { it.copy(phase = TurnPhase.PLAYER_ACTION, selectedAction = PlayerAction.NONE) }
            log("${afterEffectsEnt.name} is awaiting orders.")
        } else {
            updateCombatState { it.copy(phase = TurnPhase.ENEMY_TURN) }
            executeEnemyTurn(afterEffectsEnt)
        }
    }

    fun selectAction(action: PlayerAction) {
        when (action) {
            PlayerAction.IRON_GUARD -> executeSelfAction(action)
            PlayerAction.CAUTERIZE -> {
                updateCombatState { it.copy(phase = TurnPhase.PLAYER_TARGET_ALLY, selectedAction = action) }
                log("Select ally to Cauterize.")
            }
            PlayerAction.REPOSITION -> {
                updateCombatState { it.copy(phase = TurnPhase.PLAYER_TARGET_REPOSITION, selectedAction = action) }
                log("Select adjacent ally to Reposition.")
            }
            else -> {
                updateCombatState { it.copy(phase = TurnPhase.PLAYER_TARGET_ENEMY, selectedAction = action) }
                log("Select hostile target.")
            }
        }
    }

    fun executePlayerTargetAction(targetId: String) {
        val c = _campaignState.value.combatState
        val activeEntity = c.entities.find { it.id == c.activeEntityId } ?: return
        val targetEntity = c.entities.find { it.id == targetId } ?: return
        
        if (c.phase == TurnPhase.PLAYER_TARGET_REPOSITION) {
            log("${activeEntity.name} swaps positions with ${targetEntity.name}.")
            updateCombatState { st ->
                st.copy(entities = st.entities.map {
                    if (it.id == activeEntity.id) it.copy(rank = targetEntity.rank)
                    else if (it.id == targetEntity.id) it.copy(rank = activeEntity.rank)
                    else it
                })
            }
            realignRanks(true)
            advanceTurn()
            return
        }

        val dmgBonus = activeEntity.damageBonus
        val hasKnuckles = _campaignState.value.relics.contains(Relic.SPIKED_KNUCKLES)
        val isCrit = Random.nextFloat() < 0.15f
        val critMult = if (isCrit) 1.5f else 1.0f

        when (c.selectedAction) {
            PlayerAction.HEAVY_WRENCH -> {
                val base = (20..30).random() + dmgBonus
                val dmg = (if (hasKnuckles) base * 1.15f else base.toFloat()) * critMult
                val netDmg = (dmg.toInt() - targetEntity.armor).coerceAtLeast(1)
                if (isCrit) log("CRITICAL HIT!")
                log("${activeEntity.name} crushes ${targetEntity.name} for $netDmg DMG!")
                damageEntity(targetId, netDmg, isCrit)
            }
            PlayerAction.BATTERING_RAM -> {
                val base = (15..25).random() + dmgBonus
                val dmg = (if (hasKnuckles) base * 1.15f else base.toFloat()) * critMult
                val netDmg = (dmg.toInt() - targetEntity.armor).coerceAtLeast(1)
                if (isCrit) log("CRITICAL HIT!")
                log("${activeEntity.name} rams ${targetEntity.name} ($netDmg DMG) and knocks them back!")
                damageEntity(targetId, netDmg, isCrit)
                if (!_campaignState.value.combatState.entities.first { it.id == targetId }.isDead) {
                    knockbackEntity(targetId)
                }
            }
            PlayerAction.TACTICAL_RETREAT -> {
                val base = (10..15).random() + dmgBonus
                val dmg = (if (hasKnuckles) base * 1.15f else base.toFloat()) * critMult
                val netDmg = (dmg.toInt() - targetEntity.armor).coerceAtLeast(1)
                if (isCrit) log("CRITICAL HIT!")
                log("${activeEntity.name} retreats, firing at ${targetEntity.name} ($netDmg DMG)!")
                damageEntity(targetId, netDmg, isCrit)
                if (!_campaignState.value.combatState.entities.first { it.id == activeEntity.id }.isDead) {
                    knockbackEntity(activeEntity.id)
                }
            }
            PlayerAction.CAUTERIZE -> {
                val heal = (15..25).random()
                log("${activeEntity.name} cauterizes ${targetEntity.name} for $heal HP!")
                healEntity(targetId, heal)
            }
            PlayerAction.RAD_SHOT -> {
                val base = (8..15).random() + dmgBonus
                val dmg = (if (hasKnuckles) base * 1.15f else base.toFloat()) * critMult
                val netDmg = (dmg.toInt() - targetEntity.armor).coerceAtLeast(1)
                if (isCrit) log("CRITICAL HIT!")
                log("${activeEntity.name} fires Rad Shot at ${targetEntity.name} ($netDmg DMG)!")
                damageEntity(targetId, netDmg, isCrit)
                applyStatusEffect(targetId, StatusEffect(EffectType.RADIATION, 2))
            }
            PlayerAction.PIPE_RIFLE -> {
                val base = (15..25).random() + dmgBonus
                val dmg = (if (hasKnuckles) base * 1.15f else base.toFloat()) * critMult
                val netDmg = (dmg.toInt() - targetEntity.armor).coerceAtLeast(1)
                if (isCrit) log("CRITICAL HIT!")
                log("${activeEntity.name} snipes ${targetEntity.name} for $netDmg DMG!")
                damageEntity(targetId, netDmg, isCrit)
            }
            PlayerAction.FLASHBANG -> {
                log("${activeEntity.name} throws a Flashbang at ${targetEntity.name}!")
                applyStatusEffect(targetId, StatusEffect(EffectType.STUN, 1))
            }
            else -> {}
        }
        
        if (checkCombatEnd()) return
        advanceTurn()
    }

    private fun executeSelfAction(action: PlayerAction) {
        val c = _campaignState.value.combatState
        val activeEntity = c.entities.find { it.id == c.activeEntityId } ?: return

        when (action) {
            PlayerAction.IRON_GUARD -> {
                log("${activeEntity.name} deploys Iron Guard! (+15 Armor)")
                spawnFloatingText(activeEntity.id, "+15 ARMOR", DullSteel)
                updateCombatState { st ->
                    st.copy(entities = st.entities.map {
                        if (it.id == activeEntity.id) it.copy(armor = 15) else it
                    })
                }
            }
            else -> {}
        }
        advanceTurn()
    }

    private fun executeEnemyTurn(enemyArg: Entity) {
        viewModelScope.launch {
            delay(800)
            val c = _campaignState.value.combatState
            val enemy = c.entities.find { it.id == enemyArg.id }
            
            if (enemy == null || enemy.intent == null) {
                delay(400)
                if (checkCombatEnd()) return@launch
                advanceTurn()
                return@launch
            }
            
            val intent = enemy.intent
            val validTargets = c.entities.filter { it.isPlayer && !it.isDead }
            var target = validTargets.find { it.id == intent.targetId }
            
            if (target == null && intent.type != IntentType.BUFF && intent.type != IntentType.AOE_ATTACK) {
                target = validTargets.randomOrNull()
            }
            
            if (target != null || intent.type == IntentType.BUFF || intent.type == IntentType.AOE_ATTACK) {
                when (intent.type) {
                    IntentType.ATTACK -> {
                        val net = (intent.value - target!!.armor).coerceAtLeast(1)
                        log("${enemy.name} uses ${intent.desc} on ${target.name} for $net DMG!")
                        triggerHaptic(HapticType.HEAVY)
                        damageEntity(target.id, net)
                    }
                    IntentType.AOE_ATTACK -> {
                        log("${enemy.name} uses ${intent.desc} (AOE)!")
                        triggerHaptic(HapticType.PULSE)
                        triggerShake()
                        validTargets.forEach { t ->
                            val net = (intent.value - t.armor).coerceAtLeast(1)
                            damageEntity(t.id, net)
                        }
                    }
                    IntentType.DEBUFF -> {
                        log("${enemy.name} uses ${intent.desc} on ${target!!.name}!")
                        if (intent.desc.contains("Toxic")) {
                            applyStatusEffect(target.id, StatusEffect(EffectType.RADIATION, 3))
                        } else if (intent.desc.contains("Stun")) {
                            applyStatusEffect(target.id, StatusEffect(EffectType.STUN, 1))
                        }
                    }
                    IntentType.BUFF -> {
                        log("${enemy.name} uses ${intent.desc}!")
                        if (intent.desc.contains("Armor")) {
                            updateCombatState { st ->
                                st.copy(entities = st.entities.map {
                                    if (it.id == enemy.id) it.copy(armor = it.armor + 5) else it
                                })
                            }
                        }
                    }
                }
            }
            
            updateCombatState { st ->
                st.copy(entities = st.entities.map {
                    if (it.id == enemy.id) it.copy(intent = null) else it
                })
            }
            generateIntents()
            
            delay(400)
            if (checkCombatEnd()) return@launch
            advanceTurn()
        }
    }

    private fun damageEntity(entityId: String, amount: Int, isCrit: Boolean = false, prefix: String = "") {
        var entityDied = false
        var entityName = ""
        var isPlayerTeam = false

        if (isCrit) {
            triggerShake()
            triggerHaptic(HapticType.HEAVY)
        }
        
        val color = if (isCrit) StunYellow else RustRed
        val floatMsg = if (isCrit) "CRIT -$amount" else "$prefix-$amount"
        spawnFloatingText(entityId, floatMsg, color)

        updateCombatState { state ->
            val newEntities = state.entities.map {
                if (it.id == entityId) {
                    val oldHp = it.hp
                    val newHp = (it.hp - amount).coerceAtLeast(0)
                    val dead = newHp == 0
                    if (dead && !it.isDead) {
                        entityDied = true
                        entityName = it.name
                        isPlayerTeam = it.isPlayer
                    }
                    
                    val wasAbove25 = oldHp > it.maxHp * 0.25f
                    val isBelow25 = newHp > 0 && newHp <= it.maxHp * 0.25f
                    val hasAdrenaline = _campaignState.value.relics.contains(Relic.ADRENALINE_INJECTOR)
                    
                    val newEffects = if (it.isPlayer && hasAdrenaline && wasAbove25 && isBelow25) {
                        spawnFloatingText(it.id, "ADRENALINE!", CyanGlow)
                        triggerHaptic(HapticType.LIGHT)
                        val currentEffects = it.statusEffects.toMutableList()
                        currentEffects.add(StatusEffect(EffectType.ADRENALINE, 2))
                        currentEffects
                    } else {
                        it.statusEffects
                    }
                    it.copy(hp = newHp, isDead = dead, statusEffects = newEffects)
                } else it
            }
            state.copy(entities = newEntities)
        }

        if (entityDied) {
            log("$entityName was DESTROYED.")
            realignRanks(isPlayerTeam)
        }
    }

    private fun realignRanks(isPlayerTeam: Boolean) {
        updateCombatState { st ->
            val team = st.entities.filter { it.isPlayer == isPlayerTeam && !it.isDead }.sortedBy { it.rank }
            val updated = team.mapIndexed { i, e -> e.id to (i + 1) }.toMap()
            st.copy(entities = st.entities.map {
                if (it.isPlayer == isPlayerTeam && !it.isDead) {
                    it.copy(rank = updated[it.id] ?: it.rank)
                } else {
                    it
                }
            })
        }
    }

    private fun knockbackEntity(id: String) {
        val s = _campaignState.value.combatState
        val target = s.entities.find { it.id == id } ?: return
        val team = s.entities.filter { it.isPlayer == target.isPlayer && !it.isDead }.sortedBy { it.rank }
        val idx = team.indexOfFirst { it.id == id }
        if (idx >= 0 && idx < team.size - 1) {
            val swapped = team[idx + 1]
            updateCombatState { st ->
                st.copy(entities = st.entities.map {
                    if (it.id == target.id) it.copy(rank = swapped.rank)
                    else if (it.id == swapped.id) it.copy(rank = target.rank)
                    else it
                })
            }
            realignRanks(target.isPlayer)
        }
    }

    private fun healEntity(entityId: String, amount: Int) {
        spawnFloatingText(entityId, "+$amount", ToxicGreen)
        updateCombatState { state ->
            val newEntities = state.entities.map {
                if (it.id == entityId) it.copy(hp = (it.hp + amount).coerceAtMost(it.maxHp)) else it
            }
            state.copy(entities = newEntities)
        }
    }

    private fun applyStatusEffect(entityId: String, effect: StatusEffect) {
        spawnFloatingText(entityId, "+${effect.type.name}", ToxicGreen)
        updateCombatState { state ->
            val newEntities = state.entities.map {
                if (it.id == entityId) {
                    val currentEffects = it.statusEffects.toMutableList()
                    currentEffects.add(effect)
                    it.copy(statusEffects = currentEffects)
                } else it
            }
            state.copy(entities = newEntities)
        }
    }

    fun cancelAction() {
        updateCombatState { it.copy(phase = TurnPhase.PLAYER_ACTION, selectedAction = PlayerAction.NONE) }
        log("Orders cancelled.")
    }

    fun collectRewardAndContinue() {
        val s = _campaignState.value
        val newRelics = if (s.rewardRelic != null) s.relics + s.rewardRelic else s.relics
        _campaignState.update { it.copy(
            gameState = GameState.MAP,
            scrap = it.scrap + it.rewardScrap,
            totalScrapCollected = it.totalScrapCollected + it.rewardScrap,
            rewardScrap = 0,
            rewardRelic = null,
            relics = newRelics
        )}
        saveGame()
    }

    fun campHeal() {
        val s = _campaignState.value
        if (s.scrap >= 25) {
            val newRoster = s.roster.map {
                if (!it.isDead) it.copy(hp = (it.hp + (it.maxHp * 0.4f).toInt()).coerceAtMost(it.maxHp)) else it
            }
            _campaignState.update { it.copy(scrap = it.scrap - 25, roster = newRoster) }
            saveGame()
        }
    }

    fun campUpgrade(entityId: String, isAttack: Boolean) {
        val s = _campaignState.value
        if (s.scrap >= 50) {
            val newRoster = s.roster.map {
                if (it.id == entityId) {
                    if (isAttack) it.copy(damageBonus = it.damageBonus + 5)
                    else it.copy(maxHp = it.maxHp + 20, hp = it.hp + 20)
                } else it
            }
            _campaignState.update { it.copy(scrap = it.scrap - 50, roster = newRoster) }
            saveGame()
        }
    }
    
    fun leaveCamp() {
        _campaignState.update { it.copy(gameState = GameState.MAP) }
        saveGame()
    }

    fun resolveEvent(choice: Int) {
        val s = _campaignState.value
        val ev = s.eventState ?: return
        var newScrap = s.scrap
        val newRoster = s.roster.toMutableList()
        var newRelics = s.relics
        var outcomeTxt = ""

        when (choice) {
            1 -> {
                if (kotlin.random.Random.nextFloat() < 0.6f) {
                    val unownedRelics = Relic.values().filter { !s.relics.contains(it) }
                    val r = unownedRelics.randomOrNull()
                    if (r != null) {
                        newRelics = newRelics + r
                        outcomeTxt = "Success! You extracted the ${r.title} and 40 Scrap."
                    } else {
                        outcomeTxt = "Success! You extracted 40 Scrap."
                    }
                    newScrap += 40
                } else {
                    outcomeTxt = "Failure! The tripwires detonated, dealing 25 damage to the party."
                    for (i in newRoster.indices) {
                        if (!newRoster[i].isDead) {
                            val net = (25 - newRoster[i].armor).coerceAtLeast(1)
                            val newHp = (newRoster[i].hp - net).coerceAtLeast(0)
                            newRoster[i] = newRoster[i].copy(hp = newHp, isDead = newHp == 0)
                        }
                    }
                }
            }
            2 -> {
                newScrap += 15
                outcomeTxt = "You safely salvaged 15 Scrap from the perimeter."
            }
            3 -> {
                outcomeTxt = "You left the transport untouched and walked away safely."
            }
        }
        
        val playersAlive = newRoster.any { it.isPlayer && !it.isDead }
        if (!playersAlive) {
            grantIntel(s.currentTier * 2)
            _campaignState.update { it.copy(gameState = GameState.GAME_OVER) }
            deleteSaveGame()
            return
        }

        _campaignState.update { it.copy(
            scrap = newScrap,
            totalScrapCollected = it.totalScrapCollected + (newScrap - s.scrap),
            roster = newRoster,
            relics = newRelics,
            eventState = ev.copy(resolved = true, outcome = outcomeTxt)
        )}
        saveGame()
    }

    fun finishEvent() {
        _campaignState.update { it.copy(gameState = GameState.MAP, eventState = null) }
        saveGame()
    }
}

// --- UI ---
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val crashPrefs = getSharedPreferences("crash_prefs", Context.MODE_PRIVATE)
        val crashLog = crashPrefs.getString("crash_log", null)
        crashPrefs.edit().remove("crash_log").apply()
        
        if (crashLog != null) {
            super.onCreate(savedInstanceState)
            setContent {
                androidx.compose.material3.MaterialTheme {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Red)) {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            item {
                                Text(
                                    text = "APP CRASHED:\n\n${crashLog.take(1500)}...",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            item {
                                Button(
                                    onClick = {
                                        crashPrefs.edit().remove("crash_log").apply()
                                        finish()
                                    },
                                    modifier = Modifier.padding(top = 16.dp)
                                ) {
                                    Text("CLEAR CRASH LOG & RESTART")
                                }
                            }
                        }
                    }
                }
            }
            return
        }
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = java.io.StringWriter()
            throwable.printStackTrace(java.io.PrintWriter(sw))
            android.util.Log.e("CRASH_REPORTER", "Caught crash: " + sw.toString())
            crashPrefs.edit().putString("crash_log", sw.toString()).commit()
            defaultHandler?.uncaughtException(thread, throwable)
        }

        super.onCreate(savedInstanceState)

        setContent {
            MyApplicationTheme {
                // Simplified container to prevent edge-to-edge crashes on custom skins
                Box(modifier = Modifier.fillMaxSize().background(MatteBlack)) {
                    GameRouter(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun GameRouter(modifier: Modifier = Modifier, viewModel: GameViewModel = viewModel()) {
    val state by viewModel.campaignState.collectAsState()
    val metaState by viewModel.metaState.collectAsState()
    val hasSavedGame by viewModel.hasSavedGame.collectAsState()
    
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(state.hapticSignal) {
        if (state.hapticSignal.type != HapticType.NONE) {
            when (state.hapticSignal.type) {
                HapticType.LIGHT -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                HapticType.HEAVY -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                HapticType.PULSE -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    delay(150)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    delay(150)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                else -> {}
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (state.gameState) {
            GameState.MAIN_MENU -> CitadelScreen(
                metaState = metaState,
                hasSavedGame = hasSavedGame,
                onStartRun = { viewModel.startNewRun() },
                onContinueRun = { viewModel.loadGame() },
                onUpgradeScrap = { viewModel.upgradeScrap() },
                onUpgradeHp = { viewModel.upgradeHp() }
            )
            GameState.MAP -> MapScreen(state, onNodeSelected = { viewModel.selectMapNode(it) })
            GameState.COMBAT -> CombatScreen(
                state = state.combatState,
                relics = state.relics,
                onTargetSelected = { viewModel.executePlayerTargetAction(it) },
                onActionSelected = { viewModel.selectAction(it) },
                onCancel = { viewModel.cancelAction() }
            )
            GameState.REWARD -> RewardScreen(state, onContinue = { viewModel.collectRewardAndContinue() })
            GameState.CAMP -> CampScreen(
                state = state,
                onHeal = { viewModel.campHeal() },
                onUpgrade = { id, isAtk -> viewModel.campUpgrade(id, isAtk) },
                onLeave = { viewModel.leaveCamp() }
            )
            GameState.GAME_OVER -> GameOverScreen(onRestart = { viewModel.returnToCitadel() })
            GameState.CAMPAIGN_VICTORY -> CampaignVictoryScreen(state, onRestart = { viewModel.returnToCitadel() })
            GameState.EVENT -> EventScreen(
                eventState = state.eventState!!,
                onChoice = { viewModel.resolveEvent(it) },
                onContinue = { viewModel.finishEvent() }
            )
        }
    }
}

@Composable
fun HapticButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors(),
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        content = content
    )
}

@Composable
fun RelicTray(relics: Set<Relic>, modifier: Modifier = Modifier) {
    if (relics.isEmpty()) return
    Row(modifier = modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        relics.forEach { relic ->
            Box(
                modifier = Modifier.size(24.dp).background(MatteBlack).border(1.dp, DullSteel),
                contentAlignment = Alignment.Center
            ) {
                Text(relic.title.take(1), color = CyanGlow, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun CitadelScreen(metaState: MetaState, hasSavedGame: Boolean, onStartRun: () -> Unit, onContinueRun: () -> Unit, onUpgradeScrap: () -> Unit, onUpgradeHp: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MatteBlack).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("CITADEL HUB", color = RustRed, fontSize = 36.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(24.dp))
        
        if (hasSavedGame) {
            HapticButton(
                onClick = onContinueRun,
                colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
                modifier = Modifier.fillMaxWidth(0.8f).height(56.dp)
            ) {
                Text("CONTINUE RUN", color = MatteBlack, fontFamily = FontFamily.Monospace, fontSize = 20.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        HapticButton(
            onClick = onStartRun,
            colors = ButtonDefaults.buttonColors(containerColor = ToxicGreen),
            modifier = Modifier.fillMaxWidth(0.8f).height(56.dp)
        ) {
            Text(if (hasSavedGame) "NEW RUN (Wipes Save)" else "START NEW RUN", color = MatteBlack, fontFamily = FontFamily.Monospace, fontSize = 16.sp)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text("Lifetime Runs: ${metaState.runsAttempted}", color = DullSteel, fontFamily = FontFamily.Monospace)
        Text("Warlords Defeated: ${metaState.bossesDefeated}", color = DullSteel, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))
        Text("WASTELAND INTEL: ${metaState.intel}", color = ToxicGreen, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
        
        Spacer(modifier = Modifier.height(48.dp))
        Text("PERMANENT UPGRADES", color = TerminalText, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(16.dp))
        
        HapticButton(
            onClick = onUpgradeScrap,
            enabled = metaState.intel >= 10,
            colors = ButtonDefaults.buttonColors(containerColor = AshGray)
        ) {
            Text("Scrap Reserves (10 Intel) -> +10 Start Scrap (Lvl ${metaState.bonusScrapLevel})", color = MatteBlack, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
        HapticButton(
            onClick = onUpgradeHp,
            enabled = metaState.intel >= 10,
            colors = ButtonDefaults.buttonColors(containerColor = AshGray)
        ) {
            Text("Hardened Fibers (10 Intel) -> +5 Max HP (Lvl ${metaState.bonusHpLevel})", color = MatteBlack, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
}

@Composable
fun MapScreen(state: CampaignState, onNodeSelected: (String) -> Unit) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(MatteBlack)) {
        val w = maxWidth
        val h = maxHeight
        val pxW = constraints.maxWidth.toFloat()
        val pxH = constraints.maxHeight.toFloat()
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            fun getNodePos(node: MapNode): Offset {
                val y = pxH - (pxH * 0.2f * node.tier)
                val x = pxW * node.x
                return Offset(x, y)
            }
            
            state.mapNodes.forEach { node ->
                val start = getNodePos(node)
                node.nextNodes.forEach { nextId ->
                    val nextNode = state.mapNodes.find { it.id == nextId }
                    if (nextNode != null) {
                        val end = getNodePos(nextNode)
                        drawLine(color = DullSteel, start = start, end = end, strokeWidth = 6f)
                    }
                }
            }
        }
        
        state.mapNodes.forEach { node ->
            val isVisited = state.visitedNodes.contains(node.id)
            val isCurrent = state.currentNodeId == node.id
            val isSelectable = if (state.currentTier == 0) node.tier == 1 else state.mapNodes.find { it.id == state.currentNodeId }?.nextNodes?.contains(node.id) == true

            val yOffset = h - (h * 0.2f * node.tier) - 24.dp 
            val xOffset = w * node.x - 24.dp
            
            val borderColor = when {
                isCurrent -> Color.White
                isSelectable -> ToxicGreen
                isVisited -> RustRed
                else -> DullSteel
            }
            
            val iconText = when (node.type) {
                NodeType.COMBAT -> "⚔️"
                NodeType.SCAVENGE -> "⚙️"
                NodeType.CAMP -> "⛺"
                NodeType.BOSS -> "💀"
                NodeType.EVENT -> "❓"
            }
            
            Box(
                modifier = Modifier
                    .offset(x = xOffset, y = yOffset)
                    .size(48.dp)
                    .background(MatteBlack)
                    .border(2.dp, borderColor)
                    .then(if (isSelectable) Modifier.clickable { onNodeSelected(node.id) } else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Text(iconText, fontSize = 24.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().background(MatteBlack.copy(alpha=0.8f)).padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("TIER: ${state.currentTier}/4", color = TerminalText, fontFamily = FontFamily.Monospace)
            Text("SCRAP: ${state.scrap}", color = RustRed, fontFamily = FontFamily.Monospace)
        }
        
        RelicTray(state.relics, Modifier.align(Alignment.TopEnd).padding(top = 48.dp))
    }
}

@Composable
fun RewardScreen(state: CampaignState, onContinue: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MatteBlack), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.border(4.dp, RustRed).padding(32.dp)
        ) {
            Text("ENCOUNTER CLEARED", color = RustRed, fontSize = 24.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(16.dp))
            Text("+${state.rewardScrap} Scrap Salvaged", color = ToxicGreen, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(16.dp))
            
            if (state.rewardRelic != null) {
                Text("RELIC ACQUIRED: ${state.rewardRelic.title}", color = CyanGlow, fontFamily = FontFamily.Monospace)
                Text(state.rewardRelic.desc, color = DullSteel, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            HapticButton(
                onClick = onContinue,
                colors = ButtonDefaults.buttonColors(containerColor = DullSteel)
            ) {
                Text("CONTINUE JOURNEY", color = MatteBlack, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun CampScreen(state: CampaignState, onHeal: () -> Unit, onUpgrade: (String, Boolean) -> Unit, onLeave: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MatteBlack).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("WASTELAND CAMP", color = TerminalText, fontSize = 28.sp, fontFamily = FontFamily.Monospace)
        Text("Scrap: ${state.scrap}", color = RustRed, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(24.dp))
        
        HapticButton(
            onClick = onHeal,
            colors = ButtonDefaults.buttonColors(containerColor = ToxicGreen),
            enabled = state.scrap >= 25
        ) {
            Text("FIELD REPAIRS (25) - Restore 40% HP", color = MatteBlack, fontFamily = FontFamily.Monospace)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        Text("ARMOR PLATING & CALIBRATION (50 Scrap)", color = TerminalText, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(8.dp))
        
        state.roster.filter { !it.isDead }.forEach { unit ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${unit.name.take(12)}", color = DullSteel, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Row {
                    HapticButton(
                        onClick = { onUpgrade(unit.id, true) },
                        colors = ButtonDefaults.buttonColors(containerColor = RustRed),
                        enabled = state.scrap >= 50,
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("+5 ATK", color = MatteBlack, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                    HapticButton(
                        onClick = { onUpgrade(unit.id, false) },
                        colors = ButtonDefaults.buttonColors(containerColor = FadedOlive),
                        enabled = state.scrap >= 50
                    ) {
                        Text("+20 HP", color = MatteBlack, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        HapticButton(
            onClick = onLeave,
            colors = ButtonDefaults.buttonColors(containerColor = AshGray)
        ) {
            Text("DEPART CAMP", color = MatteBlack, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun GameOverScreen(onRestart: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MatteBlack), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.border(4.dp, DriedBlood).padding(32.dp)
        ) {
            Text("SQUAD WIPED", color = RustRed, fontSize = 32.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(16.dp))
            HapticButton(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = AshGray)
            ) {
                Text("RETURN TO CITADEL", color = MatteBlack, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun CampaignVictoryScreen(state: CampaignState, onRestart: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MatteBlack), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.border(4.dp, ToxicGreen).padding(32.dp)
        ) {
            Text("CAMPAIGN VICTORY", color = ToxicGreen, fontSize = 32.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(24.dp))
            Text("WARLORD DEFEATED.", color = TerminalText, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Total Scrap: ${state.totalScrapCollected}", color = RustRed, fontFamily = FontFamily.Monospace)
            Text("Turns Taken: ${state.turnsTaken}", color = DullSteel, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(32.dp))
            HapticButton(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = AshGray)
            ) {
                Text("RETURN TO CITADEL", color = MatteBlack, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun EventScreen(eventState: EventState, onChoice: (Int) -> Unit, onContinue: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(MatteBlack), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.9f).border(2.dp, CyanGlow).padding(24.dp)
        ) {
            Text(eventState.title, color = CyanGlow, fontSize = 24.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(16.dp))
            Text(eventState.description, color = TerminalText, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(32.dp))
            
            if (eventState.resolved) {
                Text(eventState.outcome, color = ToxicGreen, fontSize = 16.sp, fontFamily = FontFamily.Monospace)
                Spacer(modifier = Modifier.height(32.dp))
                HapticButton(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(containerColor = CyanGlow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("CONTINUE", color = MatteBlack, fontFamily = FontFamily.Monospace)
                }
            } else {
                HapticButton(
                    onClick = { onChoice(1) },
                    colors = ButtonDefaults.buttonColors(containerColor = RustRed),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text("[60% Relic/Scrap | 40% -25 HP] Disarm & Loot", color = MatteBlack, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                HapticButton(
                    onClick = { onChoice(2) },
                    colors = ButtonDefaults.buttonColors(containerColor = ToxicGreen),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text("[100% +15 Scrap] Salvage Perimeter", color = MatteBlack, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                HapticButton(
                    onClick = { onChoice(3) },
                    colors = ButtonDefaults.buttonColors(containerColor = AshGray),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text("Leave It Be", color = MatteBlack, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun CombatScreen(
    state: CombatState,
    relics: Set<Relic>,
    onTargetSelected: (String) -> Unit,
    onActionSelected: (PlayerAction) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        CombatArena(
            state = state,
            relics = relics,
            onTargetSelected = onTargetSelected,
            modifier = Modifier.weight(0.7f)
        )
        CommandDeck(
            state = state,
            onActionSelected = onActionSelected,
            onCancel = onCancel,
            modifier = Modifier.weight(0.3f)
        )
    }
}

@Composable
fun FloatingTextRenderer(text: String, color: Color) {
    val offsetY = remember { Animatable(0f) }
    val alpha = remember { Animatable(1f) }
    
    LaunchedEffect(Unit) {
        launch { offsetY.animateTo(-60f, tween(800)) }
        launch { alpha.animateTo(0f, tween(800)) }
    }
    
    Text(
        text = text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        modifier = Modifier
            .offset(y = offsetY.value.dp)
            .alpha(alpha.value)
    )
}

@Composable
fun CombatArena(
    state: CombatState,
    relics: Set<Relic>,
    onTargetSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val breathOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = InfiniteRepeatableSpec(animation = tween(1500), repeatMode = RepeatMode.Reverse),
        label = "breathOffset"
    )
    val dustPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = InfiniteRepeatableSpec(animation = tween(10000, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "dustPhase"
    )

    val shakeX = remember { Animatable(0f) }
    val shakeY = remember { Animatable(0f) }
    
    LaunchedEffect(state.shakeTrigger) {
        if (state.shakeTrigger > 0L) {
            repeat(6) {
                shakeX.animateTo((8..20).random().toFloat() * if(it%2==0) 1 else -1, tween(40))
                shakeY.animateTo((8..20).random().toFloat() * if(it%2==0) 1 else -1, tween(40))
            }
            shakeX.animateTo(0f, tween(40))
            shakeY.animateTo(0f, tween(40))
        }
    }

    val haptic = LocalHapticFeedback.current

    BoxWithConstraints(modifier = modifier.fillMaxSize().offset(x = shakeX.value.dp, y = shakeY.value.dp)) {
        val w = maxWidth
        val h = maxHeight

        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = size.width
            val sh = size.height
            
            drawRect(brush = Brush.verticalGradient(colors = listOf(MatteBlack, RustRed.copy(alpha=0.5f))), size = size)
            
            val distStruct = Path().apply {
                moveTo(sw * 0.1f, sh * 0.65f)
                lineTo(sw * 0.15f, sh * 0.45f)
                lineTo(sw * 0.2f, sh * 0.65f)
                moveTo(sw * 0.7f, sh * 0.65f)
                lineTo(sw * 0.8f, sh * 0.35f)
                lineTo(sw * 0.85f, sh * 0.65f)
            }
            drawPath(path = distStruct, color = MatteBlack, style = Stroke(width = 2f))
            
            val groundTop = sh * 0.65f
            val groundPath = Path().apply {
                moveTo(0f, groundTop)
                lineTo(sw * 0.1f, groundTop - 20f)
                lineTo(sw * 0.3f, groundTop + 15f)
                lineTo(sw * 0.5f, groundTop - 10f)
                lineTo(sw * 0.7f, groundTop + 25f)
                lineTo(sw * 0.9f, groundTop - 5f)
                lineTo(sw, groundTop)
                lineTo(sw, sh)
                lineTo(0f, sh)
                close()
            }
            drawPath(
                path = groundPath,
                brush = Brush.verticalGradient(colors = listOf(DarkGround, Color.Black), startY = groundTop, endY = sh)
            )
            
            val crackPath = Path().apply {
                moveTo(sw * 0.2f, sh * 0.7f)
                lineTo(sw * 0.25f, sh * 0.75f)
                lineTo(sw * 0.22f, sh * 0.8f)
                lineTo(sw * 0.28f, sh * 0.85f)
                moveTo(sw * 0.7f, sh * 0.8f)
                lineTo(sw * 0.65f, sh * 0.85f)
                lineTo(sw * 0.68f, sh * 0.9f)
            }
            drawPath(path = crackPath, color = MatteBlack, style = Stroke(width = 3f))
            drawPath(path = crackPath, color = RustRed.copy(alpha=0.3f), style = Stroke(width = 1f))
            
            val wirePath = Path().apply {
                var x = 0f
                val step = 40f
                while (x < sw) {
                    moveTo(x, sh * 0.95f)
                    quadraticBezierTo(x + step / 2, sh * 0.9f, x + step, sh * 0.95f)
                    moveTo(x + step / 2 - 5f, sh * 0.925f - 5f)
                    lineTo(x + step / 2 + 5f, sh * 0.925f + 5f)
                    moveTo(x + step / 2 + 5f, sh * 0.925f - 5f)
                    lineTo(x + step / 2 - 5f, sh * 0.925f + 5f)
                    x += step
                }
            }
            drawPath(path = wirePath, color = DullSteel.copy(alpha=0.6f), style = Stroke(width = 2f))
            
            for (i in 0..15) {
                val startX = (sw * 1.5f / 15f) * i - (sw * 0.2f)
                val xPos = (startX + sw * dustPhase) % (sw * 1.5f) - (sw * 0.2f)
                val yPos = sh * 0.3f + (i * 23f % (sh * 0.6f))
                val radius = (i % 3) * 2f + 2f
                drawCircle(
                    color = AshGray.copy(alpha = 0.3f),
                    radius = radius,
                    center = Offset(xPos, yPos)
                )
            }
        }
        
        RelicTray(relics, Modifier.align(Alignment.TopEnd))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MatteBlack.copy(alpha = 0.6f))
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Q: ", color = AshGray, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            val aliveEntities = state.entities.filter { !it.isDead }
            aliveEntities.forEachIndexed { index, ent ->
                Text(
                    text = ent.name.take(3).uppercase(),
                    color = if (ent.id == state.activeEntityId) RustRed else TerminalText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
                if (index < aliveEntities.lastIndex) {
                    Text(" > ", color = AshGray, fontSize = 10.sp)
                }
            }
        }

        val baseCharWidth = w * 0.10f
        val baseCharHeight = h * 0.22f

        state.entities.forEach { entity ->
            val isBoss = entity.entityType == EntityType.BOSS
            val isBruiser = entity.entityType == EntityType.BRUISER
            val charWidth = if (isBoss) baseCharWidth * 2.0f else if (isBruiser) baseCharWidth * 2.2f else baseCharWidth
            val charHeight = if (isBoss) baseCharHeight * 1.8f else if (isBruiser) baseCharHeight * 1.6f else baseCharHeight
            val groundLevel = h * 0.7f - charHeight

            val xPos = when {
                entity.isPlayer && entity.rank == 1 -> w * 0.32f
                entity.isPlayer && entity.rank == 2 -> w * 0.16f
                entity.isPlayer && entity.rank == 3 -> w * 0.02f
                isBoss -> w * 0.60f
                !entity.isPlayer && entity.rank == 1 -> w * 0.52f
                !entity.isPlayer && entity.rank == 2 -> w * 0.68f
                else -> w * 0.84f
            }

            val baseLevel = if (entity.entityType == EntityType.DRONE) groundLevel - (h * 0.15f) else groundLevel
            
            val yAnim = if (!entity.isDead) {
                breathOffset * (1f + (entity.rank * 0.2f))
            } else 0f

            val activeEnt = state.entities.find { it.id == state.activeEntityId }
            val isActive = activeEnt?.id == entity.id
            val isEnemyTargetable = state.phase == TurnPhase.PLAYER_TARGET_ENEMY && !entity.isPlayer && !entity.isDead && isValidTarget(state.selectedAction, entity.rank)
            val isAllyTargetable = (state.phase == TurnPhase.PLAYER_TARGET_ALLY && entity.isPlayer && !entity.isDead) ||
                                   (state.phase == TurnPhase.PLAYER_TARGET_REPOSITION && entity.isPlayer && !entity.isDead && activeEnt != null && kotlin.math.abs(entity.rank - activeEnt.rank) == 1)
            val isTargetable = isEnemyTargetable || isAllyTargetable

            Box(
                modifier = Modifier
                    .offset(x = xPos, y = baseLevel + yAnim.dp)
                    .size(charWidth, charHeight)
                    .graphicsLayer { alpha = if (entity.isDead) 0.2f else 1f }
                    .then(
                        if (isTargetable) Modifier.clickable { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onTargetSelected(entity.id) 
                        }
                        else Modifier
                    )
            ) {
                if (!entity.isDead) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-52).dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (!entity.isPlayer && entity.intent != null) {
                            Text(
                                text = entity.intent.desc,
                                color = TerminalText,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .background(MatteBlack.copy(alpha = 0.7f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        if (entity.statusEffects.isNotEmpty()) {
                            Row {
                                entity.statusEffects.forEach { effect ->
                                    val color = when(effect.type) {
                                        EffectType.RADIATION -> ToxicGreen
                                        EffectType.STUN -> StunYellow
                                        EffectType.ADRENALINE -> CyanGlow
                                    }
                                    val label = when(effect.type) {
                                        EffectType.RADIATION -> "RAD"
                                        EffectType.STUN -> "STN"
                                        EffectType.ADRENALINE -> "ADR"
                                    }
                                    Text(
                                        text = label, 
                                        color = MatteBlack, 
                                        fontSize = 8.sp, 
                                        fontFamily = FontFamily.Monospace, 
                                        modifier = Modifier.background(color).padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.size(2.dp))
                                }
                            }
                        }
                        if (entity.armor > 0) {
                            Text("ARM:${entity.armor}", color = DullSteel, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                        }
                        
                        Text(
                            text = "${entity.hp}/${entity.maxHp}",
                            color = TerminalText,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(8.dp)
                        ) {
                            val r = 2f
                            val cw = size.width
                            val ch = size.height
                            
                            drawRect(color = ScrapBrown)
                            drawRect(color = DullSteel, style = Stroke(width = 2f))
                            
                            drawCircle(Color.Black, radius = r, center = Offset(4f, 4f))
                            drawCircle(Color.Black, radius = r, center = Offset(cw - 4f, 4f))
                            drawCircle(Color.Black, radius = r, center = Offset(4f, ch - 4f))
                            drawCircle(Color.Black, radius = r, center = Offset(cw - 4f, ch - 4f))
                            
                            val hpRatio = if (entity.maxHp > 0) entity.hp.toFloat() / entity.maxHp else 0f
                            val fillW = cw * hpRatio
                            
                            drawRect(color = Color(0xFF400000), topLeft = Offset(0f, 0f), size = Size(cw, ch))
                            drawRect(color = Color(0xFFA00000), topLeft = Offset(0f, 0f), size = Size(fillW, ch))
                            drawRect(color = Color.White.copy(alpha=0.15f), topLeft = Offset(0f, 0f), size = Size(fillW, ch * 0.3f))
                        }
                    }
                }

                if (isActive && !entity.isDead) {
                    Box(modifier = Modifier.fillMaxSize().border(2.dp, AshGray))
                } else if (isTargetable) {
                    Box(modifier = Modifier.fillMaxSize().border(2.dp, ToxicGreen))
                }

                if (entity.entityType == EntityType.BRUISER) {
                    Image(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.hero_bruiser),
                        contentDescription = "Scrap Bruiser",
                        modifier = Modifier
                            .fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    
                    val shadowY = h * 0.85f - yAnim.dp.toPx()
                    drawOval(
                        color = Color.Black.copy(alpha = 0.6f),
                        topLeft = Offset(w * 0.1f, shadowY),
                        size = Size(w * 0.8f, h * 0.15f)
                    )

                    when (entity.entityType) {
                        EntityType.BRUISER -> {
                            // Drawn via Image component above
                        }
                        EntityType.MEDIC -> {
                            val coatPath = Path().apply {
                                moveTo(w * 0.4f, h * 0.3f)
                                lineTo(w * 0.6f, h * 0.3f)
                                lineTo(w * 0.8f, h * 0.9f)
                                lineTo(w * 0.2f, h * 0.9f)
                                close()
                            }
                            drawPath(path = coatPath, color = FadedOlive)
                            drawPath(path = coatPath, color = MatteBlack, style = Stroke(width = 4f))

                            drawCircle(color = DullSteel, radius = w * 0.25f, center = Offset(w * 0.5f, h * 0.25f))
                            drawCircle(color = MatteBlack, radius = w * 0.08f, center = Offset(w * 0.4f, h * 0.25f))
                            drawCircle(color = MatteBlack, radius = w * 0.08f, center = Offset(w * 0.6f, h * 0.25f))
                            drawCircle(color = MatteBlack, radius = w * 0.1f, center = Offset(w * 0.5f, h * 0.35f))

                            drawRoundRect(color = MatteBlack, topLeft = Offset(w * 0.7f, h * 0.5f), size = Size(w * 0.15f, h * 0.15f), cornerRadius = CornerRadius(4f))
                            drawRect(color = ToxicGreen, topLeft = Offset(w * 0.75f, h * 0.3f), size = Size(w * 0.08f, h * 0.2f))
                            drawRect(color = DullSteel, topLeft = Offset(w * 0.78f, h * 0.2f), size = Size(w * 0.02f, h * 0.1f))
                        }
                        EntityType.SCAVENGER -> {
                            val cloakPath = Path().apply {
                                moveTo(w * 0.5f, h * 0.1f)
                                lineTo(w * 0.7f, h * 0.3f)
                                lineTo(w * 0.9f, h * 0.8f)
                                lineTo(w * 0.8f, h * 0.9f)
                                lineTo(w * 0.6f, h * 0.85f)
                                lineTo(w * 0.4f, h * 0.9f)
                                lineTo(w * 0.2f, h * 0.8f)
                                lineTo(w * 0.1f, h * 0.8f)
                                lineTo(w * 0.3f, h * 0.3f)
                                close()
                            }
                            drawPath(path = cloakPath, color = ScrapBrown)
                            drawPath(path = cloakPath, color = MatteBlack, style = Stroke(width = 4f))

                            drawCircle(color = MatteBlack, radius = w * 0.15f, center = Offset(w * 0.5f, h * 0.25f))

                            drawRect(color = RustRed, topLeft = Offset(w * 0.2f, h * 0.5f), size = Size(w * 1.2f, h * 0.06f))
                            drawRect(color = DullSteel, topLeft = Offset(w * 0.8f, h * 0.45f), size = Size(w * 0.2f, h * 0.15f))
                        }
                        EntityType.RAIDER -> {
                            val bodyPath = Path().apply {
                                moveTo(w * 0.6f, h * 0.3f)
                                lineTo(w * 0.8f, h * 0.8f)
                                lineTo(w * 0.4f, h * 0.9f)
                                lineTo(w * 0.3f, h * 0.4f)
                                close()
                            }
                            drawPath(path = bodyPath, color = DullSteel)
                            drawPath(path = bodyPath, color = DriedBlood, style = Stroke(width = 3f))

                            drawCircle(color = AshGray, radius = w * 0.2f, center = Offset(w * 0.35f, h * 0.25f))

                            val mohawkPath = Path().apply {
                                moveTo(w * 0.45f, h * 0.1f)
                                lineTo(w * 0.55f, 0f)
                                lineTo(w * 0.6f, h * 0.15f)
                                lineTo(w * 0.7f, h * 0.05f)
                                lineTo(w * 0.65f, h * 0.2f)
                                close()
                            }
                            drawPath(path = mohawkPath, color = RustRed)

                            drawRect(color = MatteBlack, topLeft = Offset(w * 0.5f, h * 0.4f), size = Size(w * 0.1f, h * 0.3f))
                            val backCleaver = Path().apply {
                                moveTo(w * 0.4f, h * 0.4f)
                                lineTo(w * 0.8f, h * 0.3f)
                                lineTo(w * 0.9f, h * 0.6f)
                                lineTo(w * 0.5f, h * 0.5f)
                                close()
                            }
                            drawPath(path = backCleaver, color = RustRed)

                            drawRect(color = MatteBlack, topLeft = Offset(w * 0.2f, h * 0.5f), size = Size(w * 0.1f, h * 0.3f))
                            val frontCleaver = Path().apply {
                                moveTo(w * 0.1f, h * 0.5f)
                                lineTo(w * 0.5f, h * 0.4f)
                                lineTo(w * 0.6f, h * 0.7f)
                                lineTo(w * 0.2f, h * 0.6f)
                                close()
                            }
                            drawPath(path = frontCleaver, color = ScrapBrown)
                        }
                        EntityType.MUTANT -> {
                            val bodyPath = Path().apply {
                                moveTo(w * 0.7f, h * 0.2f)
                                lineTo(w * 0.9f, h * 0.9f)
                                lineTo(w * 0.2f, h * 0.9f)
                                lineTo(w * 0.3f, h * 0.3f)
                                close()
                            }
                            drawPath(path = bodyPath, color = FadedOlive)
                            drawPath(path = bodyPath, color = MatteBlack, style = Stroke(width = 4f))

                            drawCircle(color = DriedBlood, radius = w * 0.15f, center = Offset(w * 0.4f, h * 0.15f))

                            drawOval(color = FadedOlive, topLeft = Offset(w * 0.05f, h * 0.25f), size = Size(w * 0.4f, h * 0.5f))
                            drawLine(color = DriedBlood, start = Offset(w * 0.1f, h * 0.4f), end = Offset(w * 0.3f, h * 0.45f), strokeWidth = 3f)
                            drawLine(color = DriedBlood, start = Offset(w * 0.15f, h * 0.55f), end = Offset(w * 0.35f, h * 0.5f), strokeWidth = 3f)

                            drawRect(color = DullSteel, topLeft = Offset(w * -0.2f, h * 0.6f), size = Size(w * 0.8f, h * 0.1f))
                            drawRoundRect(color = AshGray, topLeft = Offset(w * -0.4f, h * 0.5f), size = Size(w * 0.3f, h * 0.3f), cornerRadius = CornerRadius(12f))
                        }
                        EntityType.DRONE -> {
                            drawOval(color = DullSteel, topLeft = Offset(w * 0.2f, h * 0.3f), size = Size(w * 0.6f, h * 0.4f))
                            drawOval(color = RustRed, topLeft = Offset(w * 0.2f, h * 0.3f), size = Size(w * 0.6f, h * 0.4f), style = Stroke(width = 4f))

                            drawCircle(color = CyanGlow, radius = w * 0.1f, center = Offset(w * 0.35f, h * 0.5f))

                            drawLine(color = MatteBlack, start = Offset(w * 0.5f, h * 0.3f), end = Offset(w * 0.4f, h * 0.1f), strokeWidth = 4f)
                            drawLine(color = MatteBlack, start = Offset(w * 0.5f, h * 0.3f), end = Offset(w * 0.6f, h * 0.1f), strokeWidth = 4f)
                        }
                        EntityType.BOSS -> {
                            val capePath = Path().apply {
                                moveTo(w * 0.7f, h * 0.2f)
                                lineTo(w * 0.95f, h * 0.9f)
                                lineTo(w * 0.5f, h * 0.9f)
                                close()
                            }
                            drawPath(path = capePath, color = DriedBlood)

                            drawRoundRect(color = MatteBlack, topLeft = Offset(w * 0.3f, h * 0.3f), size = Size(w * 0.5f, h * 0.6f), cornerRadius = CornerRadius(16f))
                            drawRoundRect(color = DullSteel, topLeft = Offset(w * 0.3f, h * 0.3f), size = Size(w * 0.5f, h * 0.6f), cornerRadius = CornerRadius(16f), style = Stroke(width = 6f))

                            val pauldronPath = Path().apply {
                                moveTo(w * 0.2f, h * 0.4f)
                                lineTo(w * 0.4f, h * 0.2f)
                                lineTo(w * 0.6f, h * 0.35f)
                                close()
                            }
                            drawPath(path = pauldronPath, color = RustRed)

                            drawCircle(color = AshGray, radius = w * 0.15f, center = Offset(w * 0.4f, h * 0.2f))
                            drawCircle(color = Color.Red, radius = w * 0.03f, center = Offset(w * 0.35f, h * 0.2f))
                            drawCircle(color = Color.Red, radius = w * 0.03f, center = Offset(w * 0.42f, h * 0.2f))

                            drawRect(color = DullSteel, topLeft = Offset(w * -0.1f, h * 0.5f), size = Size(w * 0.6f, h * 0.15f))
                            for (i in 0..4) {
                                drawRect(color = ScrapBrown, topLeft = Offset(w * (-0.1f + i * 0.1f), h * 0.65f), size = Size(w * 0.05f, h * 0.05f))
                            }
                        }
                    }
                }

                Box(modifier = Modifier.align(Alignment.Center).offset(y = (-30).dp)) {
                    state.floatingTexts.filter { it.entityId == entity.id }.forEach { ft ->
                        key(ft.id) {
                            FloatingTextRenderer(text = ft.text, color = ft.color)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommandDeck(
    state: CombatState,
    onActionSelected: (PlayerAction) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(MatteBlack)
            .border(width = 6.dp, color = AshGray)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            when (state.phase) {
                TurnPhase.ENEMY_TURN -> {
                    Text("ENEMY TURN...", color = TerminalText, fontFamily = FontFamily.Monospace)
                }
                TurnPhase.PLAYER_TARGET_ENEMY, TurnPhase.PLAYER_TARGET_ALLY, TurnPhase.PLAYER_TARGET_REPOSITION -> {
                    Text("SELECT TARGET...", color = TerminalText, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(8.dp))
                    HapticButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(containerColor = DullSteel)
                    ) {
                        Text("CANCEL", color = MatteBlack, fontFamily = FontFamily.Monospace)
                    }
                }
                TurnPhase.PLAYER_ACTION -> {
                    val activeEnt = state.entities.find { it.id == state.activeEntityId }
                    if (activeEnt != null && !activeEnt.isDead) {
                        val rank = activeEnt.rank
                        when (activeEnt.entityType) {
                            EntityType.BRUISER -> {
                                ActionButton("HEAVY WRENCH", DullSteel, canCast(PlayerAction.HEAVY_WRENCH, rank)) { onActionSelected(PlayerAction.HEAVY_WRENCH) }
                                ActionButton("BATT. RAM", RustRed, canCast(PlayerAction.BATTERING_RAM, rank)) { onActionSelected(PlayerAction.BATTERING_RAM) }
                                ActionButton("IRON GUARD", FadedOlive, canCast(PlayerAction.IRON_GUARD, rank)) { onActionSelected(PlayerAction.IRON_GUARD) }
                                ActionButton("REPOSITION", AshGray, canCast(PlayerAction.REPOSITION, rank)) { onActionSelected(PlayerAction.REPOSITION) }
                            }
                            EntityType.MEDIC -> {
                                ActionButton("CAUTERIZE", ToxicGreen, canCast(PlayerAction.CAUTERIZE, rank)) { onActionSelected(PlayerAction.CAUTERIZE) }
                                ActionButton("RAD SHOT", FadedOlive, canCast(PlayerAction.RAD_SHOT, rank)) { onActionSelected(PlayerAction.RAD_SHOT) }
                                ActionButton("REPOSITION", AshGray, canCast(PlayerAction.REPOSITION, rank)) { onActionSelected(PlayerAction.REPOSITION) }
                            }
                            EntityType.SCAVENGER -> {
                                ActionButton("PIPE RIFLE", DullSteel, canCast(PlayerAction.PIPE_RIFLE, rank)) { onActionSelected(PlayerAction.PIPE_RIFLE) }
                                ActionButton("FLASHBANG", StunYellow, canCast(PlayerAction.FLASHBANG, rank)) { onActionSelected(PlayerAction.FLASHBANG) }
                                ActionButton("RETREAT", AshGray, canCast(PlayerAction.TACTICAL_RETREAT, rank)) { onActionSelected(PlayerAction.TACTICAL_RETREAT) }
                                ActionButton("REPOSITION", AshGray, canCast(PlayerAction.REPOSITION, rank)) { onActionSelected(PlayerAction.REPOSITION) }
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .border(1.dp, DullSteel)
                .padding(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.log) { logItem ->
                    Text(
                        text = "> $logItem",
                        color = TerminalText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ActionButton(text: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    HapticButton(
        onClick = { if (enabled) onClick() },
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) color else color.copy(alpha = 0.3f),
            disabledContainerColor = color.copy(alpha = 0.3f)
        ),
        enabled = enabled
    ) {
        Text(text, color = if (enabled) MatteBlack else AshGray, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}
