package com.rustscape.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rustscape.client.game.SkillType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * RuneScape Sprite System
 *
 * Provides procedurally generated placeholder sprites for:
 * - Skill icons (23 skills)
 * - Item sprites (weapons, armor, resources, etc.)
 * - Equipment slot icons
 * - UI icons (orbs, buttons, etc.)
 *
 * These are placeholder sprites that can be replaced with actual RS assets.
 */

/**
 * Skill icon colors matching RS
 */
object SkillColors {
    val Attack = Color(0xFF9B0000)
    val Strength = Color(0xFF00A000)
    val Defence = Color(0xFF6090B0)
    val Hitpoints = Color(0xFFB00000)
    val Ranged = Color(0xFF00B000)
    val Prayer = Color(0xFFB0B0B0)
    val Magic = Color(0xFF4040FF)
    val Cooking = Color(0xFF8B4513)
    val Woodcutting = Color(0xFF654321)
    val Fletching = Color(0xFF228B22)
    val Fishing = Color(0xFF1E90FF)
    val Firemaking = Color(0xFFFF8C00)
    val Crafting = Color(0xFFDAA520)
    val Smithing = Color(0xFF4B4B4B)
    val Mining = Color(0xFF8B7355)
    val Herblore = Color(0xFF228B22)
    val Agility = Color(0xFF1C1C8C)
    val Thieving = Color(0xFF800080)
    val Slayer = Color(0xFF2F4F4F)
    val Farming = Color(0xFF006400)
    val Runecraft = Color(0xFFFF6347)
    val Hunter = Color(0xFF8B4513)
    val Construction = Color(0xFFCD853F)
    val Summoning = Color(0xFF00CED1)
}

/**
 * Item category for sprite generation
 */
enum class ItemCategory {
    WEAPON_SWORD,
    WEAPON_AXE,
    WEAPON_BOW,
    WEAPON_STAFF,
    ARMOR_HELMET,
    ARMOR_BODY,
    ARMOR_LEGS,
    ARMOR_SHIELD,
    ARMOR_BOOTS,
    ARMOR_GLOVES,
    ARMOR_CAPE,
    ARMOR_AMULET,
    ARMOR_RING,
    RESOURCE_ORE,
    RESOURCE_LOG,
    RESOURCE_FISH,
    RESOURCE_HERB,
    FOOD,
    POTION,
    RUNE,
    COIN,
    GEM,
    BONE,
    ARROW,
    MISC
}

/**
 * Metal/material types for coloring
 */
enum class MaterialType(val primaryColor: Color, val secondaryColor: Color, val highlightColor: Color) {
    BRONZE(Color(0xFFCD7F32), Color(0xFF8B5A2B), Color(0xFFDAA520)),
    IRON(Color(0xFF808080), Color(0xFF696969), Color(0xFFA9A9A9)),
    STEEL(Color(0xFF9E9E9E), Color(0xFF757575), Color(0xFFBDBDBD)),
    BLACK(Color(0xFF2F2F2F), Color(0xFF1A1A1A), Color(0xFF4A4A4A)),
    MITHRIL(Color(0xFF5050A0), Color(0xFF303080), Color(0xFF7070C0)),
    ADAMANT(Color(0xFF408040), Color(0xFF206020), Color(0xFF60A060)),
    RUNE(Color(0xFF40B0B0), Color(0xFF208080), Color(0xFF60D0D0)),
    DRAGON(Color(0xFFB03030), Color(0xFF802020), Color(0xFFD05050)),
    WOOD(Color(0xFF8B4513), Color(0xFF654321), Color(0xFFA0522D)),
    LEATHER(Color(0xFFD2691E), Color(0xFF8B4513), Color(0xFFDEB887)),
    CLOTH(Color(0xFF6A5ACD), Color(0xFF483D8B), Color(0xFF9370DB)),
    GOLD(Color(0xFFFFD700), Color(0xFFDAA520), Color(0xFFFFFF00))
}

/**
 * Skill icon composable - renders a procedural skill icon
 */
@Composable
fun RSSkillSprite(
    skillType: SkillType,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    Canvas(
        modifier = modifier.size(size)
    ) {
        val canvasSize = this.size.minDimension
        drawSkillIcon(skillType, canvasSize)
    }
}

/**
 * Item sprite composable
 */
@Composable
fun RSItemSprite(
    category: ItemCategory,
    material: MaterialType = MaterialType.IRON,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp
) {
    Canvas(
        modifier = modifier.size(size)
    ) {
        val canvasSize = this.size.minDimension
        drawItemSprite(category, material, canvasSize)
    }
}

/**
 * Equipment slot icon with placeholder
 */
@Composable
fun RSEquipmentSlotSprite(
    slotType: EquipmentSlot,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    hasItem: Boolean = false
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF3E3529),
                        Color(0xFF2B2117)
                    )
                )
            )
            .border(1.dp, Color(0xFF5C4A36), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (!hasItem) {
            Canvas(
                modifier = Modifier.size(size * 0.7f)
            ) {
                drawEquipmentSlotIcon(slotType, this.size.minDimension)
            }
        }
    }
}

/**
 * Equipment slot types
 */
enum class EquipmentSlot {
    HEAD,
    CAPE,
    AMULET,
    WEAPON,
    BODY,
    SHIELD,
    LEGS,
    GLOVES,
    BOOTS,
    RING,
    AMMO
}

// ============ Drawing Functions ============

/**
 * Draw skill icon based on skill type
 */
private fun DrawScope.drawSkillIcon(skillType: SkillType, canvasSize: Float) {
    val padding = canvasSize * 0.1f
    val iconSize = canvasSize - padding * 2

    // Background circle
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                getSkillColor(skillType).copy(alpha = 0.8f),
                getSkillColor(skillType).copy(alpha = 0.4f)
            )
        ),
        radius = canvasSize / 2,
        center = Offset(canvasSize / 2, canvasSize / 2)
    )

    // Border
    drawCircle(
        color = getSkillColor(skillType),
        radius = canvasSize / 2 - 1,
        center = Offset(canvasSize / 2, canvasSize / 2),
        style = Stroke(width = 2f)
    )

    // Draw skill-specific icon
    when (skillType) {
        SkillType.ATTACK -> drawSwordIcon(padding, iconSize)
        SkillType.STRENGTH -> drawFistIcon(padding, iconSize)
        SkillType.DEFENCE -> drawShieldIcon(padding, iconSize)
        SkillType.HITPOINTS -> drawHeartIcon(padding, iconSize)
        SkillType.RANGED -> drawBowIcon(padding, iconSize)
        SkillType.PRAYER -> drawPrayerIcon(padding, iconSize)
        SkillType.MAGIC -> drawStaffIcon(padding, iconSize)
        SkillType.COOKING -> drawCookingIcon(padding, iconSize)
        SkillType.WOODCUTTING -> drawAxeIcon(padding, iconSize)
        SkillType.FLETCHING -> drawArrowIcon(padding, iconSize)
        SkillType.FISHING -> drawFishIcon(padding, iconSize)
        SkillType.FIREMAKING -> drawFireIcon(padding, iconSize)
        SkillType.CRAFTING -> drawCraftingIcon(padding, iconSize)
        SkillType.SMITHING -> drawAnvilIcon(padding, iconSize)
        SkillType.MINING -> drawPickaxeIcon(padding, iconSize)
        SkillType.HERBLORE -> drawHerbIcon(padding, iconSize)
        SkillType.AGILITY -> drawAgilityIcon(padding, iconSize)
        SkillType.THIEVING -> drawThievingIcon(padding, iconSize)
        SkillType.SLAYER -> drawSkullIcon(padding, iconSize)
        SkillType.FARMING -> drawPlantIcon(padding, iconSize)
        SkillType.RUNECRAFT -> drawRuneIcon(padding, iconSize)
        SkillType.HUNTER -> drawHunterIcon(padding, iconSize)
        SkillType.CONSTRUCTION -> drawHammerIcon(padding, iconSize)
        SkillType.SUMMONING -> drawSummoningIcon(padding, iconSize)
    }
}

// Skill icon drawing helpers
private fun DrawScope.drawSwordIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Blade
    val bladePath = Path().apply {
        moveTo(centerX - size * 0.35f, centerY + size * 0.35f)
        lineTo(centerX + size * 0.25f, centerY - size * 0.25f)
        lineTo(centerX + size * 0.35f, centerY - size * 0.35f)
        lineTo(centerX - size * 0.25f, centerY + size * 0.25f)
        close()
    }
    drawPath(bladePath, Color.White.copy(alpha = 0.9f))

    // Handle
    drawLine(
        color = Color(0xFF8B4513),
        start = Offset(centerX - size * 0.35f, centerY + size * 0.35f),
        end = Offset(centerX - size * 0.45f, centerY + size * 0.45f),
        strokeWidth = size * 0.1f
    )

    // Crossguard
    drawLine(
        color = Color(0xFFDAA520),
        start = Offset(centerX - size * 0.25f, centerY + size * 0.15f),
        end = Offset(centerX - size * 0.05f, centerY + size * 0.35f),
        strokeWidth = size * 0.08f
    )
}

private fun DrawScope.drawFistIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Simple fist shape
    drawCircle(
        color = Color(0xFFDEB887),
        radius = size * 0.35f,
        center = Offset(centerX, centerY)
    )

    // Knuckle details
    for (i in 0..2) {
        drawCircle(
            color = Color(0xFFC4A77D),
            radius = size * 0.08f,
            center = Offset(centerX - size * 0.15f + i * size * 0.15f, centerY - size * 0.15f)
        )
    }
}

private fun DrawScope.drawShieldIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    val shieldPath = Path().apply {
        moveTo(centerX, centerY - size * 0.4f)
        lineTo(centerX + size * 0.35f, centerY - size * 0.2f)
        lineTo(centerX + size * 0.3f, centerY + size * 0.2f)
        lineTo(centerX, centerY + size * 0.4f)
        lineTo(centerX - size * 0.3f, centerY + size * 0.2f)
        lineTo(centerX - size * 0.35f, centerY - size * 0.2f)
        close()
    }

    drawPath(
        shieldPath, Brush.verticalGradient(
            colors = listOf(Color(0xFF6090B0), Color(0xFF405070))
        )
    )
    drawPath(shieldPath, Color(0xFF8AAFC0), style = Stroke(width = 2f))
}

private fun DrawScope.drawHeartIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2 + size * 0.05f

    val heartPath = Path().apply {
        moveTo(centerX, centerY + size * 0.3f)
        cubicTo(
            centerX - size * 0.5f, centerY,
            centerX - size * 0.5f, centerY - size * 0.4f,
            centerX, centerY - size * 0.15f
        )
        cubicTo(
            centerX + size * 0.5f, centerY - size * 0.4f,
            centerX + size * 0.5f, centerY,
            centerX, centerY + size * 0.3f
        )
        close()
    }

    drawPath(
        heartPath, Brush.radialGradient(
            colors = listOf(Color(0xFFFF4444), Color(0xFFB00000))
        )
    )
}

private fun DrawScope.drawBowIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Bow arc
    val bowPath = Path().apply {
        moveTo(centerX - size * 0.3f, centerY + size * 0.35f)
        quadraticTo(
            centerX - size * 0.45f, centerY,
            centerX - size * 0.3f, centerY - size * 0.35f
        )
    }
    drawPath(bowPath, Color(0xFF8B4513), style = Stroke(width = size * 0.08f))

    // String
    drawLine(
        color = Color(0xFFD2B48C),
        start = Offset(centerX - size * 0.3f, centerY - size * 0.35f),
        end = Offset(centerX - size * 0.3f, centerY + size * 0.35f),
        strokeWidth = 2f
    )

    // Arrow
    drawLine(
        color = Color(0xFF654321),
        start = Offset(centerX - size * 0.25f, centerY),
        end = Offset(centerX + size * 0.35f, centerY),
        strokeWidth = size * 0.05f
    )
}

private fun DrawScope.drawPrayerIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Prayer hands / star burst
    for (i in 0..5) {
        val angle = i * 60.0 * PI / 180.0
        val length = if (i % 2 == 0) size * 0.35f else size * 0.2f
        drawLine(
            color = Color.White,
            start = Offset(centerX, centerY),
            end = Offset(
                centerX + (cos(angle) * length).toFloat(),
                centerY + (sin(angle) * length).toFloat()
            ),
            strokeWidth = size * 0.06f
        )
    }
}

private fun DrawScope.drawStaffIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Staff body
    drawLine(
        color = Color(0xFF8B4513),
        start = Offset(centerX - size * 0.3f, centerY + size * 0.4f),
        end = Offset(centerX + size * 0.1f, centerY - size * 0.3f),
        strokeWidth = size * 0.1f
    )

    // Orb
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF6060FF), Color(0xFF2020A0))
        ),
        radius = size * 0.2f,
        center = Offset(centerX + size * 0.15f, centerY - size * 0.35f)
    )
}

private fun DrawScope.drawCookingIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Pot/pan
    drawOval(
        color = Color(0xFF4A4A4A),
        topLeft = Offset(centerX - size * 0.3f, centerY - size * 0.1f),
        size = Size(size * 0.6f, size * 0.4f)
    )

    // Steam
    for (i in 0..2) {
        drawLine(
            color = Color.White.copy(alpha = 0.5f),
            start = Offset(centerX - size * 0.15f + i * size * 0.15f, centerY - size * 0.15f),
            end = Offset(centerX - size * 0.1f + i * size * 0.15f, centerY - size * 0.35f),
            strokeWidth = 2f
        )
    }
}

private fun DrawScope.drawAxeIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Handle
    drawLine(
        color = Color(0xFF8B4513),
        start = Offset(centerX - size * 0.3f, centerY + size * 0.35f),
        end = Offset(centerX + size * 0.2f, centerY - size * 0.15f),
        strokeWidth = size * 0.08f
    )

    // Axe head
    val axePath = Path().apply {
        moveTo(centerX + size * 0.1f, centerY - size * 0.35f)
        lineTo(centerX + size * 0.35f, centerY - size * 0.15f)
        lineTo(centerX + size * 0.2f, centerY)
        lineTo(centerX + size * 0.1f, centerY - size * 0.1f)
        close()
    }
    drawPath(axePath, Color(0xFF808080))
}

private fun DrawScope.drawArrowIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Shaft
    drawLine(
        color = Color(0xFF654321),
        start = Offset(centerX - size * 0.35f, centerY + size * 0.2f),
        end = Offset(centerX + size * 0.2f, centerY - size * 0.25f),
        strokeWidth = size * 0.05f
    )

    // Arrowhead
    val arrowPath = Path().apply {
        moveTo(centerX + size * 0.35f, centerY - size * 0.4f)
        lineTo(centerX + size * 0.15f, centerY - size * 0.2f)
        lineTo(centerX + size * 0.25f, centerY - size * 0.3f)
        close()
    }
    drawPath(arrowPath, Color(0xFF808080))

    // Fletching
    drawLine(
        color = Color(0xFF228B22),
        start = Offset(centerX - size * 0.35f, centerY + size * 0.2f),
        end = Offset(centerX - size * 0.25f, centerY + size * 0.35f),
        strokeWidth = size * 0.06f
    )
}

private fun DrawScope.drawFishIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Fish body
    val fishPath = Path().apply {
        moveTo(centerX + size * 0.35f, centerY)
        quadraticTo(centerX, centerY - size * 0.3f, centerX - size * 0.25f, centerY)
        quadraticTo(centerX, centerY + size * 0.3f, centerX + size * 0.35f, centerY)
        close()
    }
    drawPath(fishPath, Color(0xFF4682B4))

    // Tail
    val tailPath = Path().apply {
        moveTo(centerX - size * 0.25f, centerY)
        lineTo(centerX - size * 0.4f, centerY - size * 0.2f)
        lineTo(centerX - size * 0.4f, centerY + size * 0.2f)
        close()
    }
    drawPath(tailPath, Color(0xFF4682B4))

    // Eye
    drawCircle(
        color = Color.White,
        radius = size * 0.06f,
        center = Offset(centerX + size * 0.15f, centerY - size * 0.05f)
    )
}

private fun DrawScope.drawFireIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Outer flame
    val flamePath = Path().apply {
        moveTo(centerX, centerY + size * 0.35f)
        quadraticTo(centerX - size * 0.35f, centerY, centerX - size * 0.15f, centerY - size * 0.2f)
        quadraticTo(centerX - size * 0.1f, centerY - size * 0.4f, centerX, centerY - size * 0.35f)
        quadraticTo(centerX + size * 0.1f, centerY - size * 0.4f, centerX + size * 0.15f, centerY - size * 0.2f)
        quadraticTo(centerX + size * 0.35f, centerY, centerX, centerY + size * 0.35f)
        close()
    }
    drawPath(
        flamePath, Brush.verticalGradient(
            colors = listOf(Color(0xFFFFFF00), Color(0xFFFF8C00), Color(0xFFFF4500))
        )
    )
}

private fun DrawScope.drawCraftingIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Needle/thread
    drawLine(
        color = Color(0xFFC0C0C0),
        start = Offset(centerX - size * 0.3f, centerY + size * 0.3f),
        end = Offset(centerX + size * 0.3f, centerY - size * 0.3f),
        strokeWidth = size * 0.06f
    )

    // Thread loops
    val threadPath = Path().apply {
        moveTo(centerX - size * 0.1f, centerY + size * 0.1f)
        quadraticTo(centerX - size * 0.25f, centerY, centerX - size * 0.1f, centerY - size * 0.1f)
        quadraticTo(centerX + size * 0.05f, centerY - size * 0.2f, centerX + size * 0.1f, centerY - size * 0.1f)
    }
    drawPath(threadPath, Color(0xFFDAA520), style = Stroke(width = 2f))
}

private fun DrawScope.drawAnvilIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Anvil shape
    val anvilPath = Path().apply {
        moveTo(centerX - size * 0.35f, centerY + size * 0.2f)
        lineTo(centerX - size * 0.3f, centerY - size * 0.1f)
        lineTo(centerX - size * 0.15f, centerY - size * 0.2f)
        lineTo(centerX + size * 0.25f, centerY - size * 0.2f)
        lineTo(centerX + size * 0.35f, centerY - size * 0.1f)
        lineTo(centerX + size * 0.35f, centerY + size * 0.2f)
        close()
    }
    drawPath(anvilPath, Color(0xFF4A4A4A))

    // Highlight
    drawLine(
        color = Color(0xFF6A6A6A),
        start = Offset(centerX - size * 0.15f, centerY - size * 0.18f),
        end = Offset(centerX + size * 0.25f, centerY - size * 0.18f),
        strokeWidth = size * 0.04f
    )
}

private fun DrawScope.drawPickaxeIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Handle
    drawLine(
        color = Color(0xFF8B4513),
        start = Offset(centerX - size * 0.3f, centerY + size * 0.3f),
        end = Offset(centerX + size * 0.15f, centerY - size * 0.15f),
        strokeWidth = size * 0.08f
    )

    // Pick head
    val pickPath = Path().apply {
        moveTo(centerX, centerY - size * 0.35f)
        lineTo(centerX + size * 0.35f, centerY - size * 0.1f)
        lineTo(centerX + size * 0.2f, centerY)
        lineTo(centerX + size * 0.05f, centerY - size * 0.1f)
        close()
    }
    drawPath(pickPath, Color(0xFF808080))
}

private fun DrawScope.drawHerbIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Stem
    drawLine(
        color = Color(0xFF228B22),
        start = Offset(centerX, centerY + size * 0.35f),
        end = Offset(centerX, centerY - size * 0.1f),
        strokeWidth = size * 0.05f
    )

    // Leaves
    for (angle in listOf(-45.0, 45.0, -30.0, 30.0)) {
        val rad = angle * PI / 180.0
        drawOval(
            color = Color(0xFF32CD32),
            topLeft = Offset(
                centerX - size * 0.1f + (cos(rad) * size * 0.15f).toFloat(),
                centerY - size * 0.2f + (sin(rad) * size * 0.1f).toFloat()
            ),
            size = Size(size * 0.2f, size * 0.15f)
        )
    }
}

private fun DrawScope.drawAgilityIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Running figure (simplified)
    // Head
    drawCircle(
        color = Color(0xFFDEB887),
        radius = size * 0.1f,
        center = Offset(centerX, centerY - size * 0.25f)
    )

    // Body
    drawLine(
        color = Color(0xFF1C1C8C),
        start = Offset(centerX, centerY - size * 0.15f),
        end = Offset(centerX - size * 0.1f, centerY + size * 0.15f),
        strokeWidth = size * 0.08f
    )

    // Legs
    drawLine(
        color = Color(0xFF1C1C8C),
        start = Offset(centerX - size * 0.1f, centerY + size * 0.15f),
        end = Offset(centerX - size * 0.25f, centerY + size * 0.35f),
        strokeWidth = size * 0.06f
    )
    drawLine(
        color = Color(0xFF1C1C8C),
        start = Offset(centerX - size * 0.1f, centerY + size * 0.15f),
        end = Offset(centerX + size * 0.15f, centerY + size * 0.25f),
        strokeWidth = size * 0.06f
    )
}

private fun DrawScope.drawThievingIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Mask/hood shape
    val maskPath = Path().apply {
        moveTo(centerX - size * 0.3f, centerY)
        quadraticTo(centerX - size * 0.3f, centerY - size * 0.35f, centerX, centerY - size * 0.35f)
        quadraticTo(centerX + size * 0.3f, centerY - size * 0.35f, centerX + size * 0.3f, centerY)
        lineTo(centerX + size * 0.2f, centerY + size * 0.2f)
        lineTo(centerX - size * 0.2f, centerY + size * 0.2f)
        close()
    }
    drawPath(maskPath, Color(0xFF2F2F2F))

    // Eyes
    drawOval(
        color = Color.White,
        topLeft = Offset(centerX - size * 0.2f, centerY - size * 0.15f),
        size = Size(size * 0.15f, size * 0.1f)
    )
    drawOval(
        color = Color.White,
        topLeft = Offset(centerX + size * 0.05f, centerY - size * 0.15f),
        size = Size(size * 0.15f, size * 0.1f)
    )
}

private fun DrawScope.drawSkullIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Skull shape
    drawOval(
        color = Color(0xFFE8E8E8),
        topLeft = Offset(centerX - size * 0.3f, centerY - size * 0.35f),
        size = Size(size * 0.6f, size * 0.5f)
    )

    // Jaw
    drawOval(
        color = Color(0xFFD8D8D8),
        topLeft = Offset(centerX - size * 0.2f, centerY + size * 0.05f),
        size = Size(size * 0.4f, size * 0.25f)
    )

    // Eye sockets
    drawCircle(
        color = Color.Black,
        radius = size * 0.08f,
        center = Offset(centerX - size * 0.12f, centerY - size * 0.1f)
    )
    drawCircle(
        color = Color.Black,
        radius = size * 0.08f,
        center = Offset(centerX + size * 0.12f, centerY - size * 0.1f)
    )
}

private fun DrawScope.drawPlantIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Stem
    drawLine(
        color = Color(0xFF228B22),
        start = Offset(centerX, centerY + size * 0.35f),
        end = Offset(centerX, centerY - size * 0.2f),
        strokeWidth = size * 0.06f
    )

    // Leaves
    val leafPath = Path().apply {
        moveTo(centerX, centerY)
        quadraticTo(centerX - size * 0.3f, centerY - size * 0.1f, centerX - size * 0.2f, centerY - size * 0.3f)
        quadraticTo(centerX - size * 0.1f, centerY - size * 0.2f, centerX, centerY - size * 0.1f)
    }
    drawPath(leafPath, Color(0xFF32CD32), style = Stroke(width = size * 0.05f))

    rotate(degrees = 180f, pivot = Offset(centerX, centerY - size * 0.05f)) {
        drawPath(leafPath, Color(0xFF32CD32), style = Stroke(width = size * 0.05f))
    }
}

private fun DrawScope.drawRuneIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Rune stone shape
    val runePath = Path().apply {
        moveTo(centerX, centerY - size * 0.35f)
        lineTo(centerX + size * 0.25f, centerY - size * 0.1f)
        lineTo(centerX + size * 0.2f, centerY + size * 0.35f)
        lineTo(centerX - size * 0.2f, centerY + size * 0.35f)
        lineTo(centerX - size * 0.25f, centerY - size * 0.1f)
        close()
    }
    drawPath(
        runePath, Brush.verticalGradient(
            colors = listOf(Color(0xFFFF6347), Color(0xFFB22222))
        )
    )

    // Symbol
    drawLine(
        color = Color.White.copy(alpha = 0.8f),
        start = Offset(centerX, centerY - size * 0.15f),
        end = Offset(centerX, centerY + size * 0.15f),
        strokeWidth = size * 0.05f
    )
}

private fun DrawScope.drawHunterIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Paw print
    // Main pad
    drawOval(
        color = Color(0xFF8B4513),
        topLeft = Offset(centerX - size * 0.15f, centerY),
        size = Size(size * 0.3f, size * 0.25f)
    )

    // Toe pads
    for (i in 0..2) {
        val x = centerX - size * 0.15f + i * size * 0.12f
        drawCircle(
            color = Color(0xFF8B4513),
            radius = size * 0.08f,
            center = Offset(x, centerY - size * 0.15f)
        )
    }
}

private fun DrawScope.drawHammerIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Handle
    drawLine(
        color = Color(0xFF8B4513),
        start = Offset(centerX - size * 0.2f, centerY + size * 0.35f),
        end = Offset(centerX + size * 0.1f, centerY - size * 0.05f),
        strokeWidth = size * 0.08f
    )

    // Hammer head
    drawRect(
        color = Color(0xFF696969),
        topLeft = Offset(centerX - size * 0.05f, centerY - size * 0.35f),
        size = Size(size * 0.35f, size * 0.25f)
    )
}

private fun DrawScope.drawSummoningIcon(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Wolf/spirit shape (simplified)
    val spiritPath = Path().apply {
        moveTo(centerX - size * 0.3f, centerY + size * 0.2f)
        quadraticTo(centerX - size * 0.35f, centerY - size * 0.1f, centerX - size * 0.15f, centerY - size * 0.3f)
        lineTo(centerX - size * 0.1f, centerY - size * 0.15f)
        lineTo(centerX + size * 0.1f, centerY - size * 0.15f)
        lineTo(centerX + size * 0.15f, centerY - size * 0.3f)
        quadraticTo(centerX + size * 0.35f, centerY - size * 0.1f, centerX + size * 0.3f, centerY + size * 0.2f)
        close()
    }
    drawPath(
        spiritPath, Brush.verticalGradient(
            colors = listOf(Color(0xFF00CED1).copy(alpha = 0.8f), Color(0xFF008B8B).copy(alpha = 0.6f))
        )
    )
}

// Item sprite drawing
private fun DrawScope.drawItemSprite(category: ItemCategory, material: MaterialType, canvasSize: Float) {
    val padding = canvasSize * 0.1f
    val itemSize = canvasSize - padding * 2

    when (category) {
        ItemCategory.WEAPON_SWORD -> drawSwordItem(padding, itemSize, material)
        ItemCategory.WEAPON_AXE -> drawAxeItem(padding, itemSize, material)
        ItemCategory.WEAPON_BOW -> drawBowItem(padding, itemSize, material)
        ItemCategory.WEAPON_STAFF -> drawStaffItem(padding, itemSize, material)
        ItemCategory.ARMOR_HELMET -> drawHelmetItem(padding, itemSize, material)
        ItemCategory.ARMOR_BODY -> drawBodyItem(padding, itemSize, material)
        ItemCategory.ARMOR_LEGS -> drawLegsItem(padding, itemSize, material)
        ItemCategory.ARMOR_SHIELD -> drawShieldItem(padding, itemSize, material)
        ItemCategory.ARMOR_BOOTS -> drawBootsItem(padding, itemSize, material)
        ItemCategory.ARMOR_GLOVES -> drawGlovesItem(padding, itemSize, material)
        ItemCategory.ARMOR_CAPE -> drawCapeItem(padding, itemSize, material)
        ItemCategory.ARMOR_AMULET -> drawAmuletItem(padding, itemSize, material)
        ItemCategory.ARMOR_RING -> drawRingItem(padding, itemSize, material)
        ItemCategory.RESOURCE_ORE -> drawOreItem(padding, itemSize, material)
        ItemCategory.RESOURCE_LOG -> drawLogItem(padding, itemSize)
        ItemCategory.RESOURCE_FISH -> drawFishItem(padding, itemSize)
        ItemCategory.RESOURCE_HERB -> drawHerbItem(padding, itemSize)
        ItemCategory.FOOD -> drawFoodItem(padding, itemSize)
        ItemCategory.POTION -> drawPotionItem(padding, itemSize)
        ItemCategory.RUNE -> drawRuneItem(padding, itemSize, material)
        ItemCategory.COIN -> drawCoinItem(padding, itemSize)
        ItemCategory.GEM -> drawGemItem(padding, itemSize, material)
        ItemCategory.BONE -> drawBoneItem(padding, itemSize)
        ItemCategory.ARROW -> drawArrowItem(padding, itemSize, material)
        ItemCategory.MISC -> drawMiscItem(padding, itemSize)
    }
}

// Item drawing helpers (simplified versions)
private fun DrawScope.drawSwordItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Blade
    val bladePath = Path().apply {
        moveTo(centerX - size * 0.1f, centerY + size * 0.4f)
        lineTo(centerX + size * 0.05f, centerY - size * 0.35f)
        lineTo(centerX + size * 0.15f, centerY - size * 0.3f)
        lineTo(centerX, centerY + size * 0.4f)
        close()
    }
    drawPath(bladePath, material.primaryColor)
    drawPath(bladePath, material.secondaryColor, style = Stroke(width = 1f))

    // Highlight
    drawLine(
        color = material.highlightColor,
        start = Offset(centerX - size * 0.02f, centerY + size * 0.3f),
        end = Offset(centerX + size * 0.08f, centerY - size * 0.25f),
        strokeWidth = 2f
    )

    // Handle
    drawLine(
        color = Color(0xFF8B4513),
        start = Offset(centerX - size * 0.05f, centerY + size * 0.4f),
        end = Offset(centerX - size * 0.15f, centerY + size * 0.5f),
        strokeWidth = size * 0.08f
    )

    // Crossguard
    drawLine(
        color = material.secondaryColor,
        start = Offset(centerX - size * 0.2f, centerY + size * 0.35f),
        end = Offset(centerX + size * 0.1f, centerY + size * 0.35f),
        strokeWidth = size * 0.06f
    )
}

private fun DrawScope.drawAxeItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Handle
    drawLine(
        color = Color(0xFF8B4513),
        start = Offset(centerX - size * 0.25f, centerY + size * 0.4f),
        end = Offset(centerX + size * 0.15f, centerY - size * 0.2f),
        strokeWidth = size * 0.08f
    )

    // Axe head
    val axePath = Path().apply {
        moveTo(centerX + size * 0.05f, centerY - size * 0.4f)
        quadraticTo(centerX + size * 0.4f, centerY - size * 0.3f, centerX + size * 0.35f, centerY)
        lineTo(centerX + size * 0.15f, centerY - size * 0.1f)
        close()
    }
    drawPath(axePath, material.primaryColor)
    drawPath(axePath, material.highlightColor, style = Stroke(width = 1f))
}

private fun DrawScope.drawBowItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Bow arc
    val bowPath = Path().apply {
        moveTo(centerX - size * 0.15f, centerY + size * 0.4f)
        quadraticTo(centerX - size * 0.4f, centerY, centerX - size * 0.15f, centerY - size * 0.4f)
    }
    drawPath(bowPath, MaterialType.WOOD.primaryColor, style = Stroke(width = size * 0.08f))

    // String
    drawLine(
        color = Color(0xFFD2B48C),
        start = Offset(centerX - size * 0.15f, centerY - size * 0.4f),
        end = Offset(centerX - size * 0.15f, centerY + size * 0.4f),
        strokeWidth = 2f
    )
}

private fun DrawScope.drawStaffItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Staff
    drawLine(
        color = MaterialType.WOOD.primaryColor,
        start = Offset(centerX - size * 0.1f, centerY + size * 0.45f),
        end = Offset(centerX + size * 0.1f, centerY - size * 0.3f),
        strokeWidth = size * 0.08f
    )

    // Orb
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(material.highlightColor, material.primaryColor)
        ),
        radius = size * 0.2f,
        center = Offset(centerX + size * 0.1f, centerY - size * 0.35f)
    )
}

private fun DrawScope.drawHelmetItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    val helmetPath = Path().apply {
        moveTo(centerX - size * 0.35f, centerY + size * 0.2f)
        lineTo(centerX - size * 0.3f, centerY - size * 0.1f)
        quadraticTo(centerX, centerY - size * 0.4f, centerX + size * 0.3f, centerY - size * 0.1f)
        lineTo(centerX + size * 0.35f, centerY + size * 0.2f)
        close()
    }
    drawPath(helmetPath, material.primaryColor)
    drawPath(helmetPath, material.secondaryColor, style = Stroke(width = 2f))

    // Visor slit
    drawLine(
        color = Color.Black,
        start = Offset(centerX - size * 0.2f, centerY),
        end = Offset(centerX + size * 0.2f, centerY),
        strokeWidth = size * 0.04f
    )
}

private fun DrawScope.drawBodyItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    val bodyPath = Path().apply {
        moveTo(centerX - size * 0.3f, centerY - size * 0.35f)
        lineTo(centerX + size * 0.3f, centerY - size * 0.35f)
        lineTo(centerX + size * 0.35f, centerY - size * 0.2f)
        lineTo(centerX + size * 0.25f, centerY + size * 0.35f)
        lineTo(centerX - size * 0.25f, centerY + size * 0.35f)
        lineTo(centerX - size * 0.35f, centerY - size * 0.2f)
        close()
    }
    drawPath(bodyPath, material.primaryColor)
    drawPath(bodyPath, material.secondaryColor, style = Stroke(width = 2f))
}

private fun DrawScope.drawLegsItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Left leg
    val leftLeg = Path().apply {
        moveTo(centerX - size * 0.25f, centerY - size * 0.3f)
        lineTo(centerX - size * 0.05f, centerY - size * 0.3f)
        lineTo(centerX - size * 0.1f, centerY + size * 0.4f)
        lineTo(centerX - size * 0.25f, centerY + size * 0.4f)
        close()
    }
    drawPath(leftLeg, material.primaryColor)

    // Right leg
    val rightLeg = Path().apply {
        moveTo(centerX + size * 0.05f, centerY - size * 0.3f)
        lineTo(centerX + size * 0.25f, centerY - size * 0.3f)
        lineTo(centerX + size * 0.25f, centerY + size * 0.4f)
        lineTo(centerX + size * 0.1f, centerY + size * 0.4f)
        close()
    }
    drawPath(rightLeg, material.primaryColor)

    // Waist
    drawRect(
        color = material.secondaryColor,
        topLeft = Offset(centerX - size * 0.25f, centerY - size * 0.35f),
        size = Size(size * 0.5f, size * 0.1f)
    )
}

private fun DrawScope.drawShieldItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    val shieldPath = Path().apply {
        moveTo(centerX, centerY - size * 0.4f)
        lineTo(centerX + size * 0.35f, centerY - size * 0.2f)
        lineTo(centerX + size * 0.3f, centerY + size * 0.25f)
        lineTo(centerX, centerY + size * 0.4f)
        lineTo(centerX - size * 0.3f, centerY + size * 0.25f)
        lineTo(centerX - size * 0.35f, centerY - size * 0.2f)
        close()
    }
    drawPath(shieldPath, material.primaryColor)
    drawPath(shieldPath, material.secondaryColor, style = Stroke(width = 2f))

    // Emblem
    drawCircle(
        color = material.highlightColor,
        radius = size * 0.12f,
        center = Offset(centerX, centerY)
    )
}

private fun DrawScope.drawBootsItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Boot shape
    val bootPath = Path().apply {
        moveTo(centerX - size * 0.2f, centerY - size * 0.3f)
        lineTo(centerX + size * 0.1f, centerY - size * 0.3f)
        lineTo(centerX + size * 0.1f, centerY + size * 0.15f)
        lineTo(centerX + size * 0.3f, centerY + size * 0.15f)
        lineTo(centerX + size * 0.3f, centerY + size * 0.35f)
        lineTo(centerX - size * 0.2f, centerY + size * 0.35f)
        close()
    }
    drawPath(bootPath, material.primaryColor)
    drawPath(bootPath, material.secondaryColor, style = Stroke(width = 1f))
}

private fun DrawScope.drawGlovesItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Glove
    drawOval(
        color = material.primaryColor,
        topLeft = Offset(centerX - size * 0.25f, centerY - size * 0.15f),
        size = Size(size * 0.5f, size * 0.5f)
    )

    // Fingers (simplified)
    for (i in 0..3) {
        drawOval(
            color = material.primaryColor,
            topLeft = Offset(centerX - size * 0.2f + i * size * 0.12f, centerY - size * 0.35f),
            size = Size(size * 0.1f, size * 0.25f)
        )
    }
}

private fun DrawScope.drawCapeItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    val capePath = Path().apply {
        moveTo(centerX - size * 0.3f, centerY - size * 0.35f)
        lineTo(centerX + size * 0.3f, centerY - size * 0.35f)
        quadraticTo(centerX + size * 0.35f, centerY + size * 0.2f, centerX, centerY + size * 0.4f)
        quadraticTo(centerX - size * 0.35f, centerY + size * 0.2f, centerX - size * 0.3f, centerY - size * 0.35f)
        close()
    }
    drawPath(capePath, material.primaryColor)
    drawPath(capePath, material.secondaryColor, style = Stroke(width = 2f))
}

private fun DrawScope.drawAmuletItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Chain
    val chainPath = Path().apply {
        moveTo(centerX - size * 0.25f, centerY - size * 0.35f)
        quadraticTo(centerX, centerY - size * 0.1f, centerX + size * 0.25f, centerY - size * 0.35f)
    }
    drawPath(chainPath, MaterialType.GOLD.primaryColor, style = Stroke(width = 2f))

    // Pendant
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(material.highlightColor, material.primaryColor)
        ),
        topLeft = Offset(centerX - size * 0.15f, centerY - size * 0.1f),
        size = Size(size * 0.3f, size * 0.4f)
    )
}

private fun DrawScope.drawRingItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Ring band
    drawCircle(
        color = MaterialType.GOLD.primaryColor,
        radius = size * 0.3f,
        center = Offset(centerX, centerY),
        style = Stroke(width = size * 0.1f)
    )

    // Gem
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(material.highlightColor, material.primaryColor)
        ),
        radius = size * 0.15f,
        center = Offset(centerX, centerY - size * 0.25f)
    )
}

private fun DrawScope.drawOreItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Rock shape
    val orePath = Path().apply {
        moveTo(centerX - size * 0.3f, centerY + size * 0.2f)
        lineTo(centerX - size * 0.35f, centerY - size * 0.1f)
        lineTo(centerX - size * 0.1f, centerY - size * 0.3f)
        lineTo(centerX + size * 0.2f, centerY - size * 0.25f)
        lineTo(centerX + size * 0.35f, centerY)
        lineTo(centerX + size * 0.25f, centerY + size * 0.3f)
        close()
    }
    drawPath(orePath, Color(0xFF696969))

    // Ore veins
    for (i in 0..2) {
        drawCircle(
            color = material.primaryColor,
            radius = size * 0.08f,
            center = Offset(
                centerX - size * 0.1f + i * size * 0.15f,
                centerY - size * 0.05f + (i % 2) * size * 0.1f
            )
        )
    }
}

private fun DrawScope.drawLogItem(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Log shape
    drawOval(
        color = Color(0xFF8B4513),
        topLeft = Offset(centerX - size * 0.4f, centerY - size * 0.15f),
        size = Size(size * 0.8f, size * 0.3f)
    )

    // End grain
    drawCircle(
        color = Color(0xFFA0522D),
        radius = size * 0.12f,
        center = Offset(centerX + size * 0.3f, centerY)
    )

    // Rings
    drawCircle(
        color = Color(0xFF654321),
        radius = size * 0.08f,
        center = Offset(centerX + size * 0.3f, centerY),
        style = Stroke(width = 1f)
    )
}

private fun DrawScope.drawFishItem(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Fish body
    val fishPath = Path().apply {
        moveTo(centerX + size * 0.35f, centerY)
        quadraticTo(centerX + size * 0.1f, centerY - size * 0.25f, centerX - size * 0.2f, centerY)
        quadraticTo(centerX + size * 0.1f, centerY + size * 0.25f, centerX + size * 0.35f, centerY)
        close()
    }
    drawPath(fishPath, Color(0xFF4682B4))

    // Tail
    val tailPath = Path().apply {
        moveTo(centerX - size * 0.2f, centerY)
        lineTo(centerX - size * 0.4f, centerY - size * 0.2f)
        lineTo(centerX - size * 0.4f, centerY + size * 0.2f)
        close()
    }
    drawPath(tailPath, Color(0xFF5F9EA0))

    // Eye
    drawCircle(
        color = Color.White,
        radius = size * 0.05f,
        center = Offset(centerX + size * 0.2f, centerY - size * 0.05f)
    )
}

private fun DrawScope.drawHerbItem(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Stem
    drawLine(
        color = Color(0xFF228B22),
        start = Offset(centerX, centerY + size * 0.35f),
        end = Offset(centerX, centerY - size * 0.15f),
        strokeWidth = size * 0.05f
    )

    // Leaves
    for (i in 0..2) {
        val yOffset = i * size * 0.15f
        drawOval(
            color = Color(0xFF32CD32),
            topLeft = Offset(centerX - size * 0.25f, centerY - size * 0.25f + yOffset),
            size = Size(size * 0.2f, size * 0.1f)
        )
        drawOval(
            color = Color(0xFF32CD32),
            topLeft = Offset(centerX + size * 0.05f, centerY - size * 0.2f + yOffset),
            size = Size(size * 0.2f, size * 0.1f)
        )
    }
}

private fun DrawScope.drawFoodItem(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Bread/meat shape
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFDEB887), Color(0xFFD2691E))
        ),
        topLeft = Offset(centerX - size * 0.35f, centerY - size * 0.2f),
        size = Size(size * 0.7f, size * 0.4f)
    )
}

private fun DrawScope.drawPotionItem(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Flask shape
    val flaskPath = Path().apply {
        moveTo(centerX - size * 0.1f, centerY - size * 0.35f)
        lineTo(centerX + size * 0.1f, centerY - size * 0.35f)
        lineTo(centerX + size * 0.1f, centerY - size * 0.2f)
        lineTo(centerX + size * 0.25f, centerY + size * 0.1f)
        lineTo(centerX + size * 0.2f, centerY + size * 0.35f)
        lineTo(centerX - size * 0.2f, centerY + size * 0.35f)
        lineTo(centerX - size * 0.25f, centerY + size * 0.1f)
        lineTo(centerX - size * 0.1f, centerY - size * 0.2f)
        close()
    }
    drawPath(flaskPath, Color(0xFF87CEEB))

    // Liquid
    val liquidPath = Path().apply {
        moveTo(centerX - size * 0.2f, centerY + size * 0.35f)
        lineTo(centerX + size * 0.2f, centerY + size * 0.35f)
        lineTo(centerX + size * 0.22f, centerY + size * 0.05f)
        lineTo(centerX - size * 0.22f, centerY + size * 0.05f)
        close()
    }
    drawPath(liquidPath, Color(0xFF00FF00).copy(alpha = 0.7f))
}

private fun DrawScope.drawRuneItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Rune shape
    val runePath = Path().apply {
        moveTo(centerX, centerY - size * 0.4f)
        lineTo(centerX + size * 0.3f, centerY - size * 0.15f)
        lineTo(centerX + size * 0.25f, centerY + size * 0.35f)
        lineTo(centerX - size * 0.25f, centerY + size * 0.35f)
        lineTo(centerX - size * 0.3f, centerY - size * 0.15f)
        close()
    }
    drawPath(runePath, material.primaryColor)
    drawPath(runePath, material.secondaryColor, style = Stroke(width = 1f))

    // Symbol
    drawCircle(
        color = material.highlightColor,
        radius = size * 0.1f,
        center = Offset(centerX, centerY)
    )
}

private fun DrawScope.drawCoinItem(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Coin
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFD700), Color(0xFFDAA520))
        ),
        radius = size * 0.35f,
        center = Offset(centerX, centerY)
    )

    // Edge
    drawCircle(
        color = Color(0xFFB8860B),
        radius = size * 0.35f,
        center = Offset(centerX, centerY),
        style = Stroke(width = 2f)
    )

    // Symbol
    drawCircle(
        color = Color(0xFFB8860B),
        radius = size * 0.15f,
        center = Offset(centerX, centerY),
        style = Stroke(width = 1f)
    )
}

private fun DrawScope.drawGemItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Gem facets
    val gemPath = Path().apply {
        moveTo(centerX, centerY - size * 0.35f)
        lineTo(centerX + size * 0.3f, centerY - size * 0.1f)
        lineTo(centerX + size * 0.2f, centerY + size * 0.3f)
        lineTo(centerX - size * 0.2f, centerY + size * 0.3f)
        lineTo(centerX - size * 0.3f, centerY - size * 0.1f)
        close()
    }
    drawPath(gemPath, material.primaryColor)

    // Highlight facet
    val highlightPath = Path().apply {
        moveTo(centerX, centerY - size * 0.35f)
        lineTo(centerX + size * 0.15f, centerY - size * 0.05f)
        lineTo(centerX, centerY + size * 0.1f)
        lineTo(centerX - size * 0.15f, centerY - size * 0.05f)
        close()
    }
    drawPath(highlightPath, material.highlightColor.copy(alpha = 0.6f))
}

private fun DrawScope.drawBoneItem(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Bone shaft
    drawLine(
        color = Color(0xFFF5F5DC),
        start = Offset(centerX - size * 0.25f, centerY + size * 0.25f),
        end = Offset(centerX + size * 0.25f, centerY - size * 0.25f),
        strokeWidth = size * 0.1f
    )

    // End knobs
    drawCircle(
        color = Color(0xFFF5F5DC),
        radius = size * 0.12f,
        center = Offset(centerX - size * 0.3f, centerY + size * 0.3f)
    )
    drawCircle(
        color = Color(0xFFF5F5DC),
        radius = size * 0.12f,
        center = Offset(centerX + size * 0.3f, centerY - size * 0.3f)
    )
}

private fun DrawScope.drawArrowItem(padding: Float, size: Float, material: MaterialType) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Shaft
    drawLine(
        color = Color(0xFF8B4513),
        start = Offset(centerX - size * 0.35f, centerY + size * 0.35f),
        end = Offset(centerX + size * 0.2f, centerY - size * 0.2f),
        strokeWidth = size * 0.04f
    )

    // Arrowhead
    val headPath = Path().apply {
        moveTo(centerX + size * 0.35f, centerY - size * 0.35f)
        lineTo(centerX + size * 0.15f, centerY - size * 0.15f)
        lineTo(centerX + size * 0.25f, centerY - size * 0.25f)
        close()
    }
    drawPath(headPath, material.primaryColor)

    // Fletching
    drawLine(
        color = Color(0xFFDC143C),
        start = Offset(centerX - size * 0.35f, centerY + size * 0.35f),
        end = Offset(centerX - size * 0.25f, centerY + size * 0.45f),
        strokeWidth = size * 0.05f
    )
}

private fun DrawScope.drawMiscItem(padding: Float, size: Float) {
    val centerX = padding + size / 2
    val centerY = padding + size / 2

    // Generic bag/sack
    val bagPath = Path().apply {
        moveTo(centerX - size * 0.25f, centerY - size * 0.2f)
        quadraticTo(centerX - size * 0.35f, centerY + size * 0.2f, centerX, centerY + size * 0.35f)
        quadraticTo(centerX + size * 0.35f, centerY + size * 0.2f, centerX + size * 0.25f, centerY - size * 0.2f)
        close()
    }
    drawPath(bagPath, Color(0xFFD2B48C))

    // Tie
    drawLine(
        color = Color(0xFF8B4513),
        start = Offset(centerX - size * 0.15f, centerY - size * 0.2f),
        end = Offset(centerX + size * 0.15f, centerY - size * 0.2f),
        strokeWidth = size * 0.06f
    )
}

// Equipment slot icon drawing
private fun DrawScope.drawEquipmentSlotIcon(slot: EquipmentSlot, canvasSize: Float) {
    val color = Color(0xFF5C4A36).copy(alpha = 0.5f)
    val padding = canvasSize * 0.15f
    val iconSize = canvasSize - padding * 2

    when (slot) {
        EquipmentSlot.HEAD -> {
            // Head silhouette
            drawOval(
                color = color,
                topLeft = Offset(padding, padding),
                size = Size(iconSize, iconSize * 0.8f)
            )
        }

        EquipmentSlot.CAPE -> {
            // Cape shape
            val path = Path().apply {
                moveTo(padding, padding + iconSize * 0.2f)
                lineTo(padding + iconSize, padding + iconSize * 0.2f)
                quadraticTo(padding + iconSize, padding + iconSize, padding + iconSize / 2, padding + iconSize)
                quadraticTo(padding, padding + iconSize, padding, padding + iconSize * 0.2f)
            }
            drawPath(path, color)
        }

        EquipmentSlot.AMULET -> {
            // Necklace
            val path = Path().apply {
                moveTo(padding, padding)
                quadraticTo(padding + iconSize / 2, padding + iconSize * 0.5f, padding + iconSize, padding)
            }
            drawPath(path, color, style = Stroke(width = iconSize * 0.1f))
            drawCircle(color, iconSize * 0.15f, Offset(padding + iconSize / 2, padding + iconSize * 0.6f))
        }

        EquipmentSlot.WEAPON -> {
            // Sword silhouette
            drawLine(color, Offset(padding, padding + iconSize), Offset(padding + iconSize, padding), iconSize * 0.15f)
            drawLine(
                color,
                Offset(padding + iconSize * 0.3f, padding + iconSize * 0.5f),
                Offset(padding + iconSize * 0.5f, padding + iconSize * 0.7f),
                iconSize * 0.1f
            )
        }

        EquipmentSlot.BODY -> {
            // Torso
            val path = Path().apply {
                moveTo(padding + iconSize * 0.2f, padding)
                lineTo(padding + iconSize * 0.8f, padding)
                lineTo(padding + iconSize, padding + iconSize * 0.3f)
                lineTo(padding + iconSize * 0.7f, padding + iconSize)
                lineTo(padding + iconSize * 0.3f, padding + iconSize)
                lineTo(padding, padding + iconSize * 0.3f)
                close()
            }
            drawPath(path, color)
        }

        EquipmentSlot.SHIELD -> {
            // Shield shape
            val path = Path().apply {
                moveTo(padding + iconSize / 2, padding)
                lineTo(padding + iconSize, padding + iconSize * 0.3f)
                lineTo(padding + iconSize * 0.8f, padding + iconSize * 0.8f)
                lineTo(padding + iconSize / 2, padding + iconSize)
                lineTo(padding + iconSize * 0.2f, padding + iconSize * 0.8f)
                lineTo(padding, padding + iconSize * 0.3f)
                close()
            }
            drawPath(path, color)
        }

        EquipmentSlot.LEGS -> {
            // Legs silhouette
            drawRect(color, Offset(padding + iconSize * 0.1f, padding), Size(iconSize * 0.35f, iconSize))
            drawRect(color, Offset(padding + iconSize * 0.55f, padding), Size(iconSize * 0.35f, iconSize))
        }

        EquipmentSlot.GLOVES -> {
            // Glove
            drawOval(color, Offset(padding, padding + iconSize * 0.3f), Size(iconSize, iconSize * 0.7f))
            for (i in 0..3) {
                drawOval(color, Offset(padding + i * iconSize * 0.2f, padding), Size(iconSize * 0.2f, iconSize * 0.4f))
            }
        }

        EquipmentSlot.BOOTS -> {
            // Boot
            val path = Path().apply {
                moveTo(padding, padding)
                lineTo(padding + iconSize * 0.4f, padding)
                lineTo(padding + iconSize * 0.4f, padding + iconSize * 0.7f)
                lineTo(padding + iconSize, padding + iconSize * 0.7f)
                lineTo(padding + iconSize, padding + iconSize)
                lineTo(padding, padding + iconSize)
                close()
            }
            drawPath(path, color)
        }

        EquipmentSlot.RING -> {
            // Ring
            drawCircle(
                color,
                iconSize * 0.4f,
                Offset(padding + iconSize / 2, padding + iconSize / 2),
                style = Stroke(width = iconSize * 0.15f)
            )
        }

        EquipmentSlot.AMMO -> {
            // Arrow/quiver
            drawLine(color, Offset(padding, padding + iconSize), Offset(padding + iconSize, padding), iconSize * 0.08f)
            drawLine(
                color,
                Offset(padding + iconSize * 0.15f, padding + iconSize * 0.85f),
                Offset(padding + iconSize * 0.85f, padding + iconSize * 0.15f),
                iconSize * 0.08f
            )
        }
    }
}

/**
 * Get skill color by type
 */
private fun getSkillColor(skillType: SkillType): Color {
    return when (skillType) {
        SkillType.ATTACK -> SkillColors.Attack
        SkillType.STRENGTH -> SkillColors.Strength
        SkillType.DEFENCE -> SkillColors.Defence
        SkillType.HITPOINTS -> SkillColors.Hitpoints
        SkillType.RANGED -> SkillColors.Ranged
        SkillType.PRAYER -> SkillColors.Prayer
        SkillType.MAGIC -> SkillColors.Magic
        SkillType.COOKING -> SkillColors.Cooking
        SkillType.WOODCUTTING -> SkillColors.Woodcutting
        SkillType.FLETCHING -> SkillColors.Fletching
        SkillType.FISHING -> SkillColors.Fishing
        SkillType.FIREMAKING -> SkillColors.Firemaking
        SkillType.CRAFTING -> SkillColors.Crafting
        SkillType.SMITHING -> SkillColors.Smithing
        SkillType.MINING -> SkillColors.Mining
        SkillType.HERBLORE -> SkillColors.Herblore
        SkillType.AGILITY -> SkillColors.Agility
        SkillType.THIEVING -> SkillColors.Thieving
        SkillType.SLAYER -> SkillColors.Slayer
        SkillType.FARMING -> SkillColors.Farming
        SkillType.RUNECRAFT -> SkillColors.Runecraft
        SkillType.HUNTER -> SkillColors.Hunter
        SkillType.CONSTRUCTION -> SkillColors.Construction
        SkillType.SUMMONING -> SkillColors.Summoning
    }
}
