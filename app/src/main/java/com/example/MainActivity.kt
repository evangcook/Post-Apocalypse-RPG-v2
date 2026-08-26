package com.example

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
enum class GameState { MAIN_MENU, MAP, COMBAT, CAMP, REWARD, GAME_OVER, CAMPAIGN_VICTORY }
enum class NodeType { COMBAT, SCAVENGE, CAMP, BOSS }

data class MapNode(
    val id: String,
    val type: NodeType,
    val tier: Int,
    val x: Float,
    val nextNodes: List<String> = emptyList()
)

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
    val isDead: Boolean = false
)

enum class TurnPhase { PLAYER_ACTION, PLAYER_TARGET_ENEMY, PLAYER_TARGET_ALLY, ENEMY_TURN }
enum class PlayerAction { HEAVY_WRENCH, IRON_GUARD, CAUTERIZE, RAD_SHOT, PIPE_RIFLE, FLASHBANG, NONE }

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
    val hapticSignal: HapticSignal = HapticSignal(HapticType.NONE)
)

// --- VIEWMODEL ---
class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("citadel_meta", Context.MODE_PRIVATE)
    
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
            MapNode("3_1", NodeType.SCAVENGE, 3, 0.25f, listOf("4_1")),
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
        }
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
        advanceTurn()
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
            return true
        }
        if (!enemiesAlive) {
            val updatedRoster = c.entities.filter { it.isPlayer }.map { it.copy(armor = 0, statusEffects = emptyList()) }
            
            if (s.currentTier == 4) {
                grantIntel(20 + s.currentTier * 2, bossDefeated = true)
                _campaignState.update { it.copy(gameState = GameState.CAMPAIGN_VICTORY, roster = updatedRoster) }
                triggerHaptic(HapticType.PULSE)
            } else {
                val baseScrap = (40..80).random() + (s.currentTier * 10)
                val finalScrap = if (s.relics.contains(Relic.SCRAP_MAGNET)) (baseScrap * 1.3f).toInt() else baseScrap
                var droppedRelic: Relic? = null
                if (s.currentTier == 3) droppedRelic = Relic.values().filter { !s.relics.contains(it) }.randomOrNull()

                _campaignState.update { it.copy(
                    gameState = GameState.REWARD, roster = updatedRoster, rewardScrap = finalScrap, rewardRelic = droppedRelic
                )}
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

    private fun executeEnemyTurn(enemy: Entity) {
        viewModelScope.launch {
            delay(800)
            val c = _campaignState.value.combatState
            val validTargets = c.entities.filter { it.isPlayer && !it.isDead }
            
            if (enemy.entityType == EntityType.BOSS) {
                val isAoe = c.bossTurnCount % 2 == 0
                updateCombatState { it.copy(bossTurnCount = it.bossTurnCount + 1) }
                
                triggerShake()
                if (isAoe) {
                    log("Warlord uses SHRAPNEL BLAST!")
                    triggerHaptic(HapticType.PULSE)
                    validTargets.forEach { t ->
                        val dmg = (20..30).random()
                        val net = (dmg - t.armor).coerceAtLeast(1)
                        damageEntity(t.id, net)
                    }
                } else {
                    val target = validTargets.randomOrNull()
                    if (target != null) {
                        log("Warlord uses SCRAP CLEAVE on ${target.name}!")
                        val dmg = (40..60).random()
                        val net = (dmg - target.armor).coerceAtLeast(1)
                        triggerHaptic(HapticType.HEAVY)
                        damageEntity(target.id, net)
                    }
                }
            } else {
                val target = validTargets.randomOrNull()
                if (target != null) {
                    var dmg = 0
                    when (enemy.entityType) {
                        EntityType.MUTANT -> dmg = (20..35).random()
                        EntityType.RAIDER -> dmg = (15..25).random()
                        EntityType.DRONE -> dmg = (5..15).random()
                        else -> dmg = 10
                    }
                    dmg = (dmg * (1f + (c.wave - 1) * 0.2f)).toInt()
                    val netDmg = (dmg - target.armor).coerceAtLeast(1)
                    log("${enemy.name} strikes ${target.name} for $netDmg DMG!")
                    triggerHaptic(HapticType.HEAVY)
                    damageEntity(target.id, netDmg)
                }
            }
            delay(400)
            if (checkCombatEnd()) return@launch
            advanceTurn()
        }
    }

    private fun damageEntity(entityId: String, amount: Int, isCrit: Boolean = false, prefix: String = "") {
        var entityDied = false
        var entityName = ""

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

        if (entityDied) log("$entityName was DESTROYED.")
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
    }

    fun campHeal() {
        val s = _campaignState.value
        if (s.scrap >= 25) {
            val newRoster = s.roster.map {
                if (!it.isDead) it.copy(hp = (it.hp + (it.maxHp * 0.4f).toInt()).coerceAtMost(it.maxHp)) else it
            }
            _campaignState.update { it.copy(scrap = it.scrap - 25, roster = newRoster) }
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
        }
    }
    
    fun leaveCamp() {
        _campaignState.update { it.copy(gameState = GameState.MAP) }
    }
}

// --- UI ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                // Trivial change to force recompilation and trigger emulator deploy
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    GameRouter(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun GameRouter(modifier: Modifier = Modifier, viewModel: GameViewModel = viewModel()) {
    val state by viewModel.campaignState.collectAsState()
    val metaState by viewModel.metaState.collectAsState()
    
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
                onStartRun = { viewModel.startNewRun() },
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
fun CitadelScreen(metaState: MetaState, onStartRun: () -> Unit, onUpgradeScrap: () -> Unit, onUpgradeHp: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MatteBlack).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("CITADEL HUB", color = RustRed, fontSize = 36.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(24.dp))
        
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
        
        Spacer(modifier = Modifier.weight(1f))
        HapticButton(
            onClick = onStartRun,
            colors = ButtonDefaults.buttonColors(containerColor = RustRed),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text("ENTER THE WASTELAND", color = MatteBlack, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
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
            drawRect(brush = Brush.verticalGradient(colors = listOf(AshGray, RustRed)), size = size)
            val groundTop = size.height * 0.65f
            val groundPath = Path().apply {
                moveTo(0f, groundTop)
                lineTo(size.width * 0.3f, groundTop - 15f)
                lineTo(size.width * 0.5f, groundTop + 10f)
                lineTo(size.width * 0.7f, groundTop - 25f)
                lineTo(size.width, groundTop)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = groundPath,
                brush = Brush.verticalGradient(colors = listOf(ScrapBrown, DarkGround), startY = groundTop, endY = size.height)
            )
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
            val charWidth = if (isBoss) baseCharWidth * 2.0f else baseCharWidth
            val charHeight = if (isBoss) baseCharHeight * 1.8f else baseCharHeight
            val groundLevel = h * 0.7f - charHeight

            val xPos = when {
                entity.isPlayer && entity.rank == 1 -> w * 0.38f
                entity.isPlayer && entity.rank == 2 -> w * 0.22f
                entity.isPlayer && entity.rank == 3 -> w * 0.06f
                isBoss -> w * 0.60f
                !entity.isPlayer && entity.rank == 1 -> w * 0.52f
                !entity.isPlayer && entity.rank == 2 -> w * 0.68f
                else -> w * 0.84f
            }

            val baseLevel = if (entity.entityType == EntityType.DRONE) groundLevel - (h * 0.15f) else groundLevel
            
            val yAnim = if (!entity.isDead) {
                breathOffset * (1f + (entity.rank * 0.2f))
            } else 0f

            val isActive = state.activeEntityId == entity.id
            val isEnemyTargetable = state.phase == TurnPhase.PLAYER_TARGET_ENEMY && !entity.isPlayer && !entity.isDead
            val isAllyTargetable = state.phase == TurnPhase.PLAYER_TARGET_ALLY && entity.isPlayer && !entity.isDead
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
                            .offset(y = (-36).dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .height(6.dp)
                                .background(MatteBlack)
                                .border(1.dp, AshGray)
                        ) {
                            val hpRatio = if (entity.maxHp > 0) entity.hp.toFloat() / entity.maxHp else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(hpRatio)
                                    .fillMaxHeight()
                                    .background(RustRed)
                            )
                        }
                    }
                }

                if (isActive && !entity.isDead) {
                    Box(modifier = Modifier.fillMaxSize().border(2.dp, AshGray))
                } else if (isTargetable) {
                    Box(modifier = Modifier.fillMaxSize().border(2.dp, ToxicGreen))
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    when (entity.entityType) {
                        EntityType.BRUISER -> {
                            drawRect(color = DullSteel, size = size)
                            drawRect(color = FadedOlive, size = size, style = Stroke(width = 6f))
                            drawRect(color = MatteBlack, topLeft = Offset(size.width * 0.2f, size.height * 0.2f), size = Size(size.width * 0.6f, size.height * 0.3f))
                        }
                        EntityType.MEDIC -> {
                            drawRect(color = FadedOlive, size = size)
                            drawRect(color = DullSteel, size = size, style = Stroke(width = 6f))
                            drawRect(color = ToxicGreen, topLeft = Offset(size.width * 0.4f, size.height * 0.4f), size = Size(size.width * 0.2f, size.height * 0.4f))
                            drawRect(color = ToxicGreen, topLeft = Offset(size.width * 0.3f, size.height * 0.5f), size = Size(size.width * 0.4f, size.height * 0.2f))
                        }
                        EntityType.SCAVENGER -> {
                            val w = size.width
                            val h = size.height
                            drawRect(color = DullSteel, topLeft = Offset(w * 0.2f, h * 0.1f), size = Size(w * 0.6f, h * 0.9f))
                            drawRect(color = FadedOlive, topLeft = Offset(w * 0.2f, h * 0.1f), size = Size(w * 0.6f, h * 0.9f), style = Stroke(width = 4f))
                        }
                        EntityType.MUTANT -> {
                            val p = Path().apply {
                                moveTo(size.width / 2, 0f)
                                lineTo(size.width, size.height * 0.4f)
                                lineTo(size.width * 0.8f, size.height)
                                lineTo(size.width * 0.2f, size.height)
                                lineTo(0f, size.height * 0.4f)
                                close()
                            }
                            drawPath(path = p, color = DriedBlood)
                            drawPath(path = p, color = RustRed, style = Stroke(width = 6f))
                        }
                        EntityType.RAIDER -> {
                            val p = Path().apply {
                                moveTo(size.width / 2, size.height * 0.1f)
                                lineTo(size.width * 0.9f, size.height * 0.5f)
                                lineTo(size.width * 0.7f, size.height)
                                lineTo(size.width * 0.3f, size.height)
                                lineTo(size.width * 0.1f, size.height * 0.5f)
                                close()
                            }
                            drawPath(path = p, color = RustRed)
                            drawPath(path = p, color = DriedBlood, style = Stroke(width = 4f))
                        }
                        EntityType.DRONE -> {
                            val p = Path().apply {
                                moveTo(size.width / 2, 0f)
                                lineTo(size.width, size.height / 2)
                                lineTo(size.width / 2, size.height)
                                lineTo(0f, size.height / 2)
                                close()
                            }
                            drawPath(path = p, color = DullSteel)
                            drawPath(path = p, color = RustRed, style = Stroke(width = 4f))
                        }
                        EntityType.BOSS -> {
                            drawRect(color = MatteBlack, size = size)
                            drawRect(color = RustRed, size = size, style = Stroke(width = 8f))
                            drawRect(color = Color.Red, topLeft = Offset(size.width * 0.2f, size.height * 0.2f), size = Size(size.width * 0.2f, size.height * 0.15f))
                            drawRect(color = Color.Red, topLeft = Offset(size.width * 0.6f, size.height * 0.2f), size = Size(size.width * 0.2f, size.height * 0.15f))
                            drawRect(color = DullSteel, topLeft = Offset(size.width * 0.3f, size.height * 0.7f), size = Size(size.width * 0.4f, size.height * 0.15f))
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
                TurnPhase.PLAYER_TARGET_ENEMY, TurnPhase.PLAYER_TARGET_ALLY -> {
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
                        when (activeEnt.entityType) {
                            EntityType.BRUISER -> {
                                ActionButton("HEAVY WRENCH", DullSteel) { onActionSelected(PlayerAction.HEAVY_WRENCH) }
                                ActionButton("IRON GUARD", FadedOlive) { onActionSelected(PlayerAction.IRON_GUARD) }
                            }
                            EntityType.MEDIC -> {
                                ActionButton("CAUTERIZE", ToxicGreen) { onActionSelected(PlayerAction.CAUTERIZE) }
                                ActionButton("RAD SHOT", FadedOlive) { onActionSelected(PlayerAction.RAD_SHOT) }
                            }
                            EntityType.SCAVENGER -> {
                                ActionButton("PIPE RIFLE", DullSteel) { onActionSelected(PlayerAction.PIPE_RIFLE) }
                                ActionButton("FLASHBANG", StunYellow) { onActionSelected(PlayerAction.FLASHBANG) }
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
fun ActionButton(text: String, color: Color, onClick: () -> Unit) {
    HapticButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp, bottom = 4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color)
    ) {
        Text(text, color = MatteBlack, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
}
