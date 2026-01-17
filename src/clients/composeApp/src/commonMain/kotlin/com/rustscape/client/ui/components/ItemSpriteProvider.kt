package com.rustscape.client.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.rustscape.client.cache.ItemDefinition

/**
 * Item sprite provider for rendering item icons
 *
 * Provides both:
 * 1. Real PNG sprites when available (loaded via SpriteLoader)
 * 2. Vector-based fallback sprites for common items
 *
 * In RS, item sprites are rendered from 3D models, but for the client
 * we use pre-rendered sprites or generated vector graphics.
 */
object ItemSpriteProvider {

    /**
     * Item categories for fallback sprite generation
     */
    enum class ItemCategory {
        WEAPON_SWORD,
        WEAPON_AXE,
        WEAPON_BOW,
        WEAPON_STAFF,
        WEAPON_DAGGER,
        WEAPON_MACE,
        WEAPON_SPEAR,
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
        RESOURCE_BAR,
        RESOURCE_LOG,
        RESOURCE_PLANK,
        RESOURCE_FISH_RAW,
        RESOURCE_FISH_COOKED,
        RESOURCE_HERB,
        RESOURCE_SEED,
        FOOD,
        POTION,
        RUNE,
        COIN,
        GEM,
        BONE,
        ARROW,
        BOLT,
        TOOL_PICKAXE,
        TOOL_HATCHET,
        TOOL_HAMMER,
        TOOL_CHISEL,
        TOOL_TINDERBOX,
        TOOL_KNIFE,
        CONTAINER_BUCKET,
        CONTAINER_JUG,
        CONTAINER_VIAL,
        KEY,
        SCROLL,
        BOOK,
        MISC
    }

    /**
     * Material/metal type for color variants
     */
    enum class MaterialType(val primaryColor: Color, val secondaryColor: Color, val highlightColor: Color) {
        BRONZE(Color(0xFFCD7F32), Color(0xFF8B5A2B), Color(0xFFDDA15E)),
        IRON(Color(0xFF707070), Color(0xFF505050), Color(0xFF909090)),
        STEEL(Color(0xFF8B8B8B), Color(0xFF6B6B6B), Color(0xFFABABAB)),
        BLACK(Color(0xFF2B2B2B), Color(0xFF1B1B1B), Color(0xFF4B4B4B)),
        MITHRIL(Color(0xFF4169E1), Color(0xFF2B4A9E), Color(0xFF6B89F1)),
        ADAMANT(Color(0xFF2E8B57), Color(0xFF1E6B47), Color(0xFF4EAB77)),
        RUNE(Color(0xFF40E0D0), Color(0xFF20C0B0), Color(0xFF60FFF0)),
        DRAGON(Color(0xFFDC143C), Color(0xFFAC0428), Color(0xFFFC3454)),
        WOOD(Color(0xFF8B4513), Color(0xFF6B3503), Color(0xFFAB6533)),
        LEATHER(Color(0xFFA0522D), Color(0xFF803010), Color(0xFFC07040)),
        GREEN_DHIDE(Color(0xFF228B22), Color(0xFF106B10), Color(0xFF42AB42)),
        BLUE_DHIDE(Color(0xFF4169E1), Color(0xFF2149C1), Color(0xFF6189FF)),
        RED_DHIDE(Color(0xFFDC143C), Color(0xFFBC0020), Color(0xFFFC345C)),
        BLACK_DHIDE(Color(0xFF2B2B2B), Color(0xFF1B1B1B), Color(0xFF4B4B4B)),
        CLOTH_WHITE(Color(0xFFF5F5F5), Color(0xFFD5D5D5), Color(0xFFFFFFFF)),
        CLOTH_BLUE(Color(0xFF4169E1), Color(0xFF2149C1), Color(0xFF6189FF)),
        GOLD(Color(0xFFFFD700), Color(0xFFDAA520), Color(0xFFFFE740)),
        SILVER(Color(0xFFC0C0C0), Color(0xFFA0A0A0), Color(0xFFE0E0E0))
    }

    /**
     * Common item ID to sprite mapping
     * These map real RS item IDs to their visual representation
     */
    private val itemCategoryMap = mapOf(
        // Coins
        995 to ItemInfo(ItemCategory.COIN, MaterialType.GOLD, "Coins"),

        // Bronze items
        1277 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.BRONZE, "Bronze sword"),
        1291 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.BRONZE, "Bronze longsword"),
        1321 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.BRONZE, "Bronze 2h sword"),
        1351 to ItemInfo(ItemCategory.TOOL_HATCHET, MaterialType.BRONZE, "Bronze axe"),
        1265 to ItemInfo(ItemCategory.TOOL_PICKAXE, MaterialType.BRONZE, "Bronze pickaxe"),
        1205 to ItemInfo(ItemCategory.WEAPON_DAGGER, MaterialType.BRONZE, "Bronze dagger"),
        1117 to ItemInfo(ItemCategory.ARMOR_BODY, MaterialType.BRONZE, "Bronze platebody"),
        1075 to ItemInfo(ItemCategory.ARMOR_LEGS, MaterialType.BRONZE, "Bronze platelegs"),
        1155 to ItemInfo(ItemCategory.ARMOR_HELMET, MaterialType.BRONZE, "Bronze full helm"),
        1139 to ItemInfo(ItemCategory.ARMOR_HELMET, MaterialType.BRONZE, "Bronze med helm"),
        1173 to ItemInfo(ItemCategory.ARMOR_SHIELD, MaterialType.BRONZE, "Bronze sq shield"),
        1189 to ItemInfo(ItemCategory.ARMOR_SHIELD, MaterialType.BRONZE, "Bronze kiteshield"),

        // Iron items
        1279 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.IRON, "Iron sword"),
        1293 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.IRON, "Iron longsword"),
        1323 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.IRON, "Iron 2h sword"),
        1349 to ItemInfo(ItemCategory.TOOL_HATCHET, MaterialType.IRON, "Iron axe"),
        1267 to ItemInfo(ItemCategory.TOOL_PICKAXE, MaterialType.IRON, "Iron pickaxe"),
        1203 to ItemInfo(ItemCategory.WEAPON_DAGGER, MaterialType.IRON, "Iron dagger"),
        1115 to ItemInfo(ItemCategory.ARMOR_BODY, MaterialType.IRON, "Iron platebody"),
        1067 to ItemInfo(ItemCategory.ARMOR_LEGS, MaterialType.IRON, "Iron platelegs"),
        1153 to ItemInfo(ItemCategory.ARMOR_HELMET, MaterialType.IRON, "Iron full helm"),
        1137 to ItemInfo(ItemCategory.ARMOR_HELMET, MaterialType.IRON, "Iron med helm"),
        1175 to ItemInfo(ItemCategory.ARMOR_SHIELD, MaterialType.IRON, "Iron sq shield"),
        1191 to ItemInfo(ItemCategory.ARMOR_SHIELD, MaterialType.IRON, "Iron kiteshield"),

        // Steel items
        1281 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.STEEL, "Steel sword"),
        1295 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.STEEL, "Steel longsword"),
        1325 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.STEEL, "Steel 2h sword"),
        1353 to ItemInfo(ItemCategory.TOOL_HATCHET, MaterialType.STEEL, "Steel axe"),
        1269 to ItemInfo(ItemCategory.TOOL_PICKAXE, MaterialType.STEEL, "Steel pickaxe"),
        1207 to ItemInfo(ItemCategory.WEAPON_DAGGER, MaterialType.STEEL, "Steel dagger"),
        1119 to ItemInfo(ItemCategory.ARMOR_BODY, MaterialType.STEEL, "Steel platebody"),
        1069 to ItemInfo(ItemCategory.ARMOR_LEGS, MaterialType.STEEL, "Steel platelegs"),
        1157 to ItemInfo(ItemCategory.ARMOR_HELMET, MaterialType.STEEL, "Steel full helm"),
        1141 to ItemInfo(ItemCategory.ARMOR_HELMET, MaterialType.STEEL, "Steel med helm"),
        1177 to ItemInfo(ItemCategory.ARMOR_SHIELD, MaterialType.STEEL, "Steel sq shield"),
        1193 to ItemInfo(ItemCategory.ARMOR_SHIELD, MaterialType.STEEL, "Steel kiteshield"),

        // Mithril items
        1285 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.MITHRIL, "Mithril sword"),
        1299 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.MITHRIL, "Mithril longsword"),
        1329 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.MITHRIL, "Mithril 2h sword"),
        1355 to ItemInfo(ItemCategory.TOOL_HATCHET, MaterialType.MITHRIL, "Mithril axe"),
        1273 to ItemInfo(ItemCategory.TOOL_PICKAXE, MaterialType.MITHRIL, "Mithril pickaxe"),
        1209 to ItemInfo(ItemCategory.WEAPON_DAGGER, MaterialType.MITHRIL, "Mithril dagger"),
        1121 to ItemInfo(ItemCategory.ARMOR_BODY, MaterialType.MITHRIL, "Mithril platebody"),
        1071 to ItemInfo(ItemCategory.ARMOR_LEGS, MaterialType.MITHRIL, "Mithril platelegs"),
        1159 to ItemInfo(ItemCategory.ARMOR_HELMET, MaterialType.MITHRIL, "Mithril full helm"),
        1143 to ItemInfo(ItemCategory.ARMOR_HELMET, MaterialType.MITHRIL, "Mithril med helm"),
        1181 to ItemInfo(ItemCategory.ARMOR_SHIELD, MaterialType.MITHRIL, "Mithril sq shield"),
        1197 to ItemInfo(ItemCategory.ARMOR_SHIELD, MaterialType.MITHRIL, "Mithril kiteshield"),

        // Adamant items
        1287 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.ADAMANT, "Adamant sword"),
        1301 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.ADAMANT, "Adamant longsword"),
        1331 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.ADAMANT, "Adamant 2h sword"),
        1357 to ItemInfo(ItemCategory.TOOL_HATCHET, MaterialType.ADAMANT, "Adamant axe"),
        1271 to ItemInfo(ItemCategory.TOOL_PICKAXE, MaterialType.ADAMANT, "Adamant pickaxe"),
        1211 to ItemInfo(ItemCategory.WEAPON_DAGGER, MaterialType.ADAMANT, "Adamant dagger"),
        1123 to ItemInfo(ItemCategory.ARMOR_BODY, MaterialType.ADAMANT, "Adamant platebody"),
        1073 to ItemInfo(ItemCategory.ARMOR_LEGS, MaterialType.ADAMANT, "Adamant platelegs"),
        1161 to ItemInfo(ItemCategory.ARMOR_HELMET, MaterialType.ADAMANT, "Adamant full helm"),
        1145 to ItemInfo(ItemCategory.ARMOR_HELMET, MaterialType.ADAMANT, "Adamant med helm"),
        1183 to ItemInfo(ItemCategory.ARMOR_SHIELD, MaterialType.ADAMANT, "Adamant sq shield"),
        1199 to ItemInfo(ItemCategory.ARMOR_SHIELD, MaterialType.ADAMANT, "Adamant kiteshield"),

        // Rune items
        1289 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.RUNE, "Rune sword"),
        1303 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.RUNE, "Rune longsword"),
        1333 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.RUNE, "Rune 2h sword"),
        1359 to ItemInfo(ItemCategory.TOOL_HATCHET, MaterialType.RUNE, "Rune axe"),
        1275 to ItemInfo(ItemCategory.TOOL_PICKAXE, MaterialType.RUNE, "Rune pickaxe"),
        1213 to ItemInfo(ItemCategory.WEAPON_DAGGER, MaterialType.RUNE, "Rune dagger"),
        1127 to ItemInfo(ItemCategory.ARMOR_BODY, MaterialType.RUNE, "Rune platebody"),
        1079 to ItemInfo(ItemCategory.ARMOR_LEGS, MaterialType.RUNE, "Rune platelegs"),
        1163 to ItemInfo(ItemCategory.ARMOR_HELMET, MaterialType.RUNE, "Rune full helm"),
        1147 to ItemInfo(ItemCategory.ARMOR_HELMET, MaterialType.RUNE, "Rune med helm"),
        1185 to ItemInfo(ItemCategory.ARMOR_SHIELD, MaterialType.RUNE, "Rune sq shield"),
        1201 to ItemInfo(ItemCategory.ARMOR_SHIELD, MaterialType.RUNE, "Rune kiteshield"),

        // Dragon items
        1215 to ItemInfo(ItemCategory.WEAPON_DAGGER, MaterialType.DRAGON, "Dragon dagger"),
        1305 to ItemInfo(ItemCategory.WEAPON_SWORD, MaterialType.DRAGON, "Dragon longsword"),
        1434 to ItemInfo(ItemCategory.WEAPON_MACE, MaterialType.DRAGON, "Dragon mace"),
        6739 to ItemInfo(ItemCategory.TOOL_HATCHET, MaterialType.DRAGON, "Dragon axe"),

        // Wooden shield
        1171 to ItemInfo(ItemCategory.ARMOR_SHIELD, MaterialType.WOOD, "Wooden shield"),

        // Bows
        841 to ItemInfo(ItemCategory.WEAPON_BOW, MaterialType.WOOD, "Shortbow"),
        839 to ItemInfo(ItemCategory.WEAPON_BOW, MaterialType.WOOD, "Longbow"),
        843 to ItemInfo(ItemCategory.WEAPON_BOW, MaterialType.WOOD, "Oak shortbow"),
        845 to ItemInfo(ItemCategory.WEAPON_BOW, MaterialType.WOOD, "Oak longbow"),
        849 to ItemInfo(ItemCategory.WEAPON_BOW, MaterialType.WOOD, "Willow shortbow"),
        847 to ItemInfo(ItemCategory.WEAPON_BOW, MaterialType.WOOD, "Willow longbow"),
        853 to ItemInfo(ItemCategory.WEAPON_BOW, MaterialType.WOOD, "Maple shortbow"),
        851 to ItemInfo(ItemCategory.WEAPON_BOW, MaterialType.WOOD, "Maple longbow"),
        857 to ItemInfo(ItemCategory.WEAPON_BOW, MaterialType.WOOD, "Yew shortbow"),
        855 to ItemInfo(ItemCategory.WEAPON_BOW, MaterialType.WOOD, "Yew longbow"),
        861 to ItemInfo(ItemCategory.WEAPON_BOW, MaterialType.WOOD, "Magic shortbow"),
        859 to ItemInfo(ItemCategory.WEAPON_BOW, MaterialType.WOOD, "Magic longbow"),

        // Staffs
        1381 to ItemInfo(ItemCategory.WEAPON_STAFF, MaterialType.WOOD, "Staff of air"),
        1383 to ItemInfo(ItemCategory.WEAPON_STAFF, MaterialType.WOOD, "Staff of water"),
        1385 to ItemInfo(ItemCategory.WEAPON_STAFF, MaterialType.WOOD, "Staff of earth"),
        1387 to ItemInfo(ItemCategory.WEAPON_STAFF, MaterialType.WOOD, "Staff of fire"),
        1389 to ItemInfo(ItemCategory.WEAPON_STAFF, MaterialType.WOOD, "Staff"),
        1391 to ItemInfo(ItemCategory.WEAPON_STAFF, MaterialType.WOOD, "Battlestaff"),

        // Arrows
        882 to ItemInfo(ItemCategory.ARROW, MaterialType.BRONZE, "Bronze arrow"),
        884 to ItemInfo(ItemCategory.ARROW, MaterialType.IRON, "Iron arrow"),
        886 to ItemInfo(ItemCategory.ARROW, MaterialType.STEEL, "Steel arrow"),
        888 to ItemInfo(ItemCategory.ARROW, MaterialType.MITHRIL, "Mithril arrow"),
        890 to ItemInfo(ItemCategory.ARROW, MaterialType.ADAMANT, "Adamant arrow"),
        892 to ItemInfo(ItemCategory.ARROW, MaterialType.RUNE, "Rune arrow"),

        // Ores
        436 to ItemInfo(ItemCategory.RESOURCE_ORE, MaterialType.BRONZE, "Copper ore"),
        438 to ItemInfo(ItemCategory.RESOURCE_ORE, MaterialType.IRON, "Tin ore"),
        440 to ItemInfo(ItemCategory.RESOURCE_ORE, MaterialType.IRON, "Iron ore"),
        442 to ItemInfo(ItemCategory.RESOURCE_ORE, MaterialType.SILVER, "Silver ore"),
        444 to ItemInfo(ItemCategory.RESOURCE_ORE, MaterialType.GOLD, "Gold ore"),
        447 to ItemInfo(ItemCategory.RESOURCE_ORE, MaterialType.MITHRIL, "Mithril ore"),
        449 to ItemInfo(ItemCategory.RESOURCE_ORE, MaterialType.ADAMANT, "Adamant ore"),
        451 to ItemInfo(ItemCategory.RESOURCE_ORE, MaterialType.RUNE, "Runite ore"),
        453 to ItemInfo(ItemCategory.RESOURCE_ORE, MaterialType.STEEL, "Coal"),

        // Bars
        2349 to ItemInfo(ItemCategory.RESOURCE_BAR, MaterialType.BRONZE, "Bronze bar"),
        2351 to ItemInfo(ItemCategory.RESOURCE_BAR, MaterialType.IRON, "Iron bar"),
        2353 to ItemInfo(ItemCategory.RESOURCE_BAR, MaterialType.STEEL, "Steel bar"),
        2355 to ItemInfo(ItemCategory.RESOURCE_BAR, MaterialType.SILVER, "Silver bar"),
        2357 to ItemInfo(ItemCategory.RESOURCE_BAR, MaterialType.GOLD, "Gold bar"),
        2359 to ItemInfo(ItemCategory.RESOURCE_BAR, MaterialType.MITHRIL, "Mithril bar"),
        2361 to ItemInfo(ItemCategory.RESOURCE_BAR, MaterialType.ADAMANT, "Adamant bar"),
        2363 to ItemInfo(ItemCategory.RESOURCE_BAR, MaterialType.RUNE, "Runite bar"),

        // Logs
        1511 to ItemInfo(ItemCategory.RESOURCE_LOG, MaterialType.WOOD, "Logs"),
        1521 to ItemInfo(ItemCategory.RESOURCE_LOG, MaterialType.WOOD, "Oak logs"),
        1519 to ItemInfo(ItemCategory.RESOURCE_LOG, MaterialType.WOOD, "Willow logs"),
        1517 to ItemInfo(ItemCategory.RESOURCE_LOG, MaterialType.WOOD, "Maple logs"),
        1515 to ItemInfo(ItemCategory.RESOURCE_LOG, MaterialType.WOOD, "Yew logs"),
        1513 to ItemInfo(ItemCategory.RESOURCE_LOG, MaterialType.WOOD, "Magic logs"),

        // Fish (raw)
        317 to ItemInfo(ItemCategory.RESOURCE_FISH_RAW, MaterialType.CLOTH_WHITE, "Raw shrimps"),
        321 to ItemInfo(ItemCategory.RESOURCE_FISH_RAW, MaterialType.CLOTH_WHITE, "Raw anchovies"),
        327 to ItemInfo(ItemCategory.RESOURCE_FISH_RAW, MaterialType.CLOTH_WHITE, "Raw sardine"),
        345 to ItemInfo(ItemCategory.RESOURCE_FISH_RAW, MaterialType.CLOTH_WHITE, "Raw herring"),
        349 to ItemInfo(ItemCategory.RESOURCE_FISH_RAW, MaterialType.CLOTH_WHITE, "Raw pike"),
        331 to ItemInfo(ItemCategory.RESOURCE_FISH_RAW, MaterialType.CLOTH_WHITE, "Raw salmon"),
        335 to ItemInfo(ItemCategory.RESOURCE_FISH_RAW, MaterialType.CLOTH_WHITE, "Raw trout"),
        359 to ItemInfo(ItemCategory.RESOURCE_FISH_RAW, MaterialType.CLOTH_WHITE, "Raw tuna"),
        371 to ItemInfo(ItemCategory.RESOURCE_FISH_RAW, MaterialType.CLOTH_WHITE, "Raw swordfish"),
        383 to ItemInfo(ItemCategory.RESOURCE_FISH_RAW, MaterialType.CLOTH_WHITE, "Raw shark"),
        377 to ItemInfo(ItemCategory.RESOURCE_FISH_RAW, MaterialType.CLOTH_WHITE, "Raw lobster"),

        // Fish (cooked)
        315 to ItemInfo(ItemCategory.RESOURCE_FISH_COOKED, MaterialType.CLOTH_WHITE, "Shrimps"),
        319 to ItemInfo(ItemCategory.RESOURCE_FISH_COOKED, MaterialType.CLOTH_WHITE, "Anchovies"),
        325 to ItemInfo(ItemCategory.RESOURCE_FISH_COOKED, MaterialType.CLOTH_WHITE, "Sardine"),
        347 to ItemInfo(ItemCategory.RESOURCE_FISH_COOKED, MaterialType.CLOTH_WHITE, "Herring"),
        351 to ItemInfo(ItemCategory.RESOURCE_FISH_COOKED, MaterialType.CLOTH_WHITE, "Pike"),
        329 to ItemInfo(ItemCategory.RESOURCE_FISH_COOKED, MaterialType.CLOTH_WHITE, "Salmon"),
        333 to ItemInfo(ItemCategory.RESOURCE_FISH_COOKED, MaterialType.CLOTH_WHITE, "Trout"),
        361 to ItemInfo(ItemCategory.RESOURCE_FISH_COOKED, MaterialType.CLOTH_WHITE, "Tuna"),
        373 to ItemInfo(ItemCategory.RESOURCE_FISH_COOKED, MaterialType.CLOTH_WHITE, "Swordfish"),
        385 to ItemInfo(ItemCategory.RESOURCE_FISH_COOKED, MaterialType.CLOTH_WHITE, "Shark"),
        379 to ItemInfo(ItemCategory.RESOURCE_FISH_COOKED, MaterialType.CLOTH_WHITE, "Lobster"),

        // Food
        1891 to ItemInfo(ItemCategory.FOOD, MaterialType.CLOTH_WHITE, "Cake"),
        2309 to ItemInfo(ItemCategory.FOOD, MaterialType.CLOTH_WHITE, "Bread"),
        1897 to ItemInfo(ItemCategory.FOOD, MaterialType.CLOTH_WHITE, "Chocolate cake"),
        1963 to ItemInfo(ItemCategory.FOOD, MaterialType.CLOTH_WHITE, "Banana"),
        1955 to ItemInfo(ItemCategory.FOOD, MaterialType.CLOTH_WHITE, "Tomato"),
        1965 to ItemInfo(ItemCategory.FOOD, MaterialType.CLOTH_WHITE, "Cabbage"),
        1942 to ItemInfo(ItemCategory.FOOD, MaterialType.CLOTH_WHITE, "Potato"),

        // Potions
        2428 to ItemInfo(ItemCategory.POTION, MaterialType.CLOTH_BLUE, "Attack potion(4)"),
        113 to ItemInfo(ItemCategory.POTION, MaterialType.CLOTH_BLUE, "Strength potion(4)"),
        2432 to ItemInfo(ItemCategory.POTION, MaterialType.CLOTH_BLUE, "Defence potion(4)"),
        3024 to ItemInfo(ItemCategory.POTION, MaterialType.CLOTH_BLUE, "Super restore(4)"),
        2434 to ItemInfo(ItemCategory.POTION, MaterialType.CLOTH_BLUE, "Prayer potion(4)"),

        // Runes
        556 to ItemInfo(ItemCategory.RUNE, MaterialType.CLOTH_WHITE, "Air rune"),
        555 to ItemInfo(ItemCategory.RUNE, MaterialType.CLOTH_BLUE, "Water rune"),
        557 to ItemInfo(ItemCategory.RUNE, MaterialType.CLOTH_WHITE, "Earth rune"),
        554 to ItemInfo(ItemCategory.RUNE, MaterialType.DRAGON, "Fire rune"),
        558 to ItemInfo(ItemCategory.RUNE, MaterialType.CLOTH_WHITE, "Mind rune"),
        559 to ItemInfo(ItemCategory.RUNE, MaterialType.CLOTH_WHITE, "Body rune"),
        564 to ItemInfo(ItemCategory.RUNE, MaterialType.CLOTH_WHITE, "Cosmic rune"),
        562 to ItemInfo(ItemCategory.RUNE, MaterialType.CLOTH_WHITE, "Chaos rune"),
        560 to ItemInfo(ItemCategory.RUNE, MaterialType.CLOTH_WHITE, "Death rune"),
        565 to ItemInfo(ItemCategory.RUNE, MaterialType.CLOTH_WHITE, "Blood rune"),
        561 to ItemInfo(ItemCategory.RUNE, MaterialType.MITHRIL, "Nature rune"),
        563 to ItemInfo(ItemCategory.RUNE, MaterialType.GOLD, "Law rune"),
        566 to ItemInfo(ItemCategory.RUNE, MaterialType.CLOTH_WHITE, "Soul rune"),

        // Gems
        1623 to ItemInfo(ItemCategory.GEM, MaterialType.DRAGON, "Uncut sapphire"),
        1621 to ItemInfo(ItemCategory.GEM, MaterialType.ADAMANT, "Uncut emerald"),
        1619 to ItemInfo(ItemCategory.GEM, MaterialType.DRAGON, "Uncut ruby"),
        1617 to ItemInfo(ItemCategory.GEM, MaterialType.CLOTH_WHITE, "Uncut diamond"),
        1607 to ItemInfo(ItemCategory.GEM, MaterialType.MITHRIL, "Sapphire"),
        1605 to ItemInfo(ItemCategory.GEM, MaterialType.ADAMANT, "Emerald"),
        1603 to ItemInfo(ItemCategory.GEM, MaterialType.DRAGON, "Ruby"),
        1601 to ItemInfo(ItemCategory.GEM, MaterialType.CLOTH_WHITE, "Diamond"),

        // Bones
        526 to ItemInfo(ItemCategory.BONE, MaterialType.CLOTH_WHITE, "Bones"),
        532 to ItemInfo(ItemCategory.BONE, MaterialType.CLOTH_WHITE, "Big bones"),
        536 to ItemInfo(ItemCategory.BONE, MaterialType.CLOTH_WHITE, "Dragon bones"),

        // Tools
        590 to ItemInfo(ItemCategory.TOOL_TINDERBOX, MaterialType.WOOD, "Tinderbox"),
        946 to ItemInfo(ItemCategory.TOOL_KNIFE, MaterialType.IRON, "Knife"),
        2347 to ItemInfo(ItemCategory.TOOL_HAMMER, MaterialType.IRON, "Hammer"),
        1755 to ItemInfo(ItemCategory.TOOL_CHISEL, MaterialType.IRON, "Chisel"),
        303 to ItemInfo(ItemCategory.MISC, MaterialType.CLOTH_WHITE, "Small fishing net"),
        307 to ItemInfo(ItemCategory.MISC, MaterialType.WOOD, "Fishing rod"),
        309 to ItemInfo(ItemCategory.MISC, MaterialType.WOOD, "Fly fishing rod"),
        311 to ItemInfo(ItemCategory.MISC, MaterialType.WOOD, "Harpoon"),
        301 to ItemInfo(ItemCategory.MISC, MaterialType.IRON, "Lobster pot"),

        // Containers
        1925 to ItemInfo(ItemCategory.CONTAINER_BUCKET, MaterialType.WOOD, "Bucket"),
        1929 to ItemInfo(ItemCategory.CONTAINER_BUCKET, MaterialType.WOOD, "Bucket of water"),
        1931 to ItemInfo(ItemCategory.CONTAINER_BUCKET, MaterialType.WOOD, "Pot"),
        1935 to ItemInfo(ItemCategory.CONTAINER_JUG, MaterialType.CLOTH_WHITE, "Jug"),
        227 to ItemInfo(ItemCategory.CONTAINER_VIAL, MaterialType.CLOTH_WHITE, "Vial"),

        // Leather armor
        1129 to ItemInfo(ItemCategory.ARMOR_BODY, MaterialType.LEATHER, "Leather body"),
        1095 to ItemInfo(ItemCategory.ARMOR_LEGS, MaterialType.LEATHER, "Leather chaps"),
        1167 to ItemInfo(ItemCategory.ARMOR_HELMET, MaterialType.LEATHER, "Leather cowl"),
        1059 to ItemInfo(ItemCategory.ARMOR_GLOVES, MaterialType.LEATHER, "Leather gloves"),
        1061 to ItemInfo(ItemCategory.ARMOR_BOOTS, MaterialType.LEATHER, "Leather boots"),
        1063 to ItemInfo(ItemCategory.ARMOR_SHIELD, MaterialType.LEATHER, "Leather vambraces"),

        // Green dragonhide
        1135 to ItemInfo(ItemCategory.ARMOR_BODY, MaterialType.GREEN_DHIDE, "Green d'hide body"),
        1099 to ItemInfo(ItemCategory.ARMOR_LEGS, MaterialType.GREEN_DHIDE, "Green d'hide chaps"),
        1065 to ItemInfo(ItemCategory.ARMOR_GLOVES, MaterialType.GREEN_DHIDE, "Green d'hide vamb"),

        // Blue dragonhide
        2499 to ItemInfo(ItemCategory.ARMOR_BODY, MaterialType.BLUE_DHIDE, "Blue d'hide body"),
        2493 to ItemInfo(ItemCategory.ARMOR_LEGS, MaterialType.BLUE_DHIDE, "Blue d'hide chaps"),
        2487 to ItemInfo(ItemCategory.ARMOR_GLOVES, MaterialType.BLUE_DHIDE, "Blue d'hide vamb"),

        // Amulets
        1704 to ItemInfo(ItemCategory.ARMOR_AMULET, MaterialType.GOLD, "Amulet of glory"),
        1725 to ItemInfo(ItemCategory.ARMOR_AMULET, MaterialType.GOLD, "Amulet of strength"),
        1731 to ItemInfo(ItemCategory.ARMOR_AMULET, MaterialType.GOLD, "Amulet of power"),
        1718 to ItemInfo(ItemCategory.ARMOR_AMULET, MaterialType.GOLD, "Amulet of magic"),

        // Rings
        2550 to ItemInfo(ItemCategory.ARMOR_RING, MaterialType.GOLD, "Ring of recoil"),
        2552 to ItemInfo(ItemCategory.ARMOR_RING, MaterialType.GOLD, "Ring of dueling"),
        2568 to ItemInfo(ItemCategory.ARMOR_RING, MaterialType.GOLD, "Ring of forging"),

        // Capes
        1019 to ItemInfo(ItemCategory.ARMOR_CAPE, MaterialType.CLOTH_WHITE, "White cape"),
        1021 to ItemInfo(ItemCategory.ARMOR_CAPE, MaterialType.CLOTH_BLUE, "Blue cape"),
        1023 to ItemInfo(ItemCategory.ARMOR_CAPE, MaterialType.DRAGON, "Red cape"),
        1007 to ItemInfo(ItemCategory.ARMOR_CAPE, MaterialType.BLACK_DHIDE, "Black cape")
    )

    /**
     * Item information for rendering
     */
    data class ItemInfo(
        val category: ItemCategory,
        val material: MaterialType,
        val name: String
    )

    /**
     * Get item info by ID
     */
    fun getItemInfo(itemId: Int): ItemInfo? = itemCategoryMap[itemId]

    /**
     * Get category for an item
     */
    fun getCategoryForItem(itemId: Int): ItemCategory {
        return itemCategoryMap[itemId]?.category ?: guessCategoryFromId(itemId)
    }

    /**
     * Get material type for an item
     */
    fun getMaterialForItem(itemId: Int): MaterialType {
        return itemCategoryMap[itemId]?.material ?: MaterialType.IRON
    }

    /**
     * Guess category from item ID (for unknown items)
     * Uses known RuneScape item ID patterns and ranges
     */
    fun guessCategoryFromId(itemId: Int): ItemCategory {
        return when {
            // Coins and common currency
            itemId == 995 -> ItemCategory.COIN

            // Herbs (grimy: 199-219, 2485, 3049-3051; clean: 249-269, 2481, 3000-3002)
            itemId in 199..219 || itemId in 249..269 ||
                    itemId in 2481..2485 || itemId in 3000..3051 -> ItemCategory.RESOURCE_HERB

            // Seeds (5283-5325, 5291-5318)
            itemId in 5283..5325 -> ItemCategory.RESOURCE_SEED

            // Runes (554-566, plus some special runes)
            itemId in 554..566 || itemId == 9075 -> ItemCategory.RUNE

            // Ores (436-453)
            itemId in 436..453 -> ItemCategory.RESOURCE_ORE

            // Bars (2349-2363)
            itemId in 2349..2363 -> ItemCategory.RESOURCE_BAR

            // Logs (1511-1521)
            itemId in 1511..1521 -> ItemCategory.RESOURCE_LOG

            // Raw fish (various ranges)
            itemId in 317..397 && itemId % 2 == 1 -> ItemCategory.RESOURCE_FISH_RAW

            // Cooked fish
            itemId in 315..395 && itemId % 2 == 1 -> ItemCategory.RESOURCE_FISH_COOKED

            // Potions (common potion ranges)
            itemId in 113..191 || itemId in 2428..2458 ||
                    itemId in 3024..3046 || itemId in 6685..6691 ||
                    itemId in 12695..12701 -> ItemCategory.POTION

            // Arrows (882-892)
            itemId in 882..892 -> ItemCategory.ARROW

            // Bolts (877, 9140-9145)
            itemId == 877 || itemId in 9140..9145 -> ItemCategory.BOLT

            // Bows (839-861)
            itemId in 839..861 -> ItemCategory.WEAPON_BOW

            // Crossbows (9174-9185, 11785)
            itemId in 9174..9185 || itemId == 11785 -> ItemCategory.WEAPON_BOW

            // Daggers (1203-1215)
            itemId in 1203..1215 -> ItemCategory.WEAPON_DAGGER

            // Swords (1277-1333)
            itemId in 1277..1333 -> ItemCategory.WEAPON_SWORD

            // Axes/hatchets (1349-1359, 6739)
            itemId in 1349..1359 || itemId == 6739 -> ItemCategory.TOOL_HATCHET

            // Pickaxes (1265-1275)
            itemId in 1265..1275 -> ItemCategory.TOOL_PICKAXE

            // Maces (1337-1347, 1434)
            itemId in 1337..1347 || itemId == 1434 -> ItemCategory.WEAPON_MACE

            // Battleaxes (1363-1373)
            itemId in 1363..1373 -> ItemCategory.WEAPON_AXE

            // Spears (1237-1251)
            itemId in 1237..1251 -> ItemCategory.WEAPON_SPEAR

            // Staffs (1379-1403, 4675, 4710, 6562-6563, 11789)
            itemId in 1379..1403 || itemId == 4675 || itemId == 4710 ||
                    itemId in 6562..6563 || itemId == 11789 -> ItemCategory.WEAPON_STAFF

            // Platebodies (1115-1127, 1135)
            itemId in 1115..1127 || itemId == 1129 || itemId == 1131 ||
                    itemId == 1135 || itemId in 2499..2503 -> ItemCategory.ARMOR_BODY

            // Platelegs/skirts (1067-1093, 1095-1099)
            itemId in 1067..1099 || itemId in 2493..2497 -> ItemCategory.ARMOR_LEGS

            // Helmets (1137-1163)
            itemId in 1137..1167 -> ItemCategory.ARMOR_HELMET

            // Shields (1171-1201)
            itemId in 1171..1201 -> ItemCategory.ARMOR_SHIELD

            // Boots (1061, 2577, 3105, 4119, 11840)
            itemId == 1061 || itemId == 2577 || itemId == 3105 ||
                    itemId == 4119 || itemId == 11840 -> ItemCategory.ARMOR_BOOTS

            // Gloves/vambraces (1059, 1063-1065, 2487-2491, 7453-7461)
            itemId == 1059 || itemId in 1063..1065 ||
                    itemId in 2487..2491 || itemId in 7453..7461 -> ItemCategory.ARMOR_GLOVES

            // Capes (1007-1027, 2412-2414, 6570, 9747-9813)
            itemId in 1007..1027 || itemId in 2412..2414 ||
                    itemId == 6570 || itemId in 9747..9813 -> ItemCategory.ARMOR_CAPE

            // Amulets (1694-1731, 4081, 6585)
            itemId in 1694..1731 || itemId == 4081 || itemId == 6585 -> ItemCategory.ARMOR_AMULET

            // Rings (2550-2572, 6465, 11770-11773)
            itemId in 2550..2572 || itemId == 6465 ||
                    itemId in 11770..11773 -> ItemCategory.ARMOR_RING

            // Bones (526-536)
            itemId in 526..536 -> ItemCategory.BONE

            // Gems (1601-1623)
            itemId in 1601..1631 -> ItemCategory.GEM

            // Food (common ranges)
            itemId in 1891..1897 || itemId in 1955..1987 ||
                    itemId in 2142..2309 || itemId in 6705..6709 ||
                    itemId == 7946 || itemId == 3144 -> ItemCategory.FOOD

            // Tools
            itemId == 590 -> ItemCategory.TOOL_TINDERBOX
            itemId == 946 -> ItemCategory.TOOL_KNIFE
            itemId == 2347 -> ItemCategory.TOOL_HAMMER
            itemId == 1755 -> ItemCategory.TOOL_CHISEL

            // Containers
            itemId in 1925..1937 -> ItemCategory.CONTAINER_BUCKET
            itemId == 227 -> ItemCategory.CONTAINER_VIAL

            // Barrows equipment (4708-4759)
            itemId in 4708..4759 -> when {
                itemId in listOf(4708, 4716, 4724, 4732, 4745, 4753) -> ItemCategory.ARMOR_HELMET
                itemId in listOf(4712, 4720, 4728, 4736, 4749, 4757) -> ItemCategory.ARMOR_BODY
                itemId in listOf(4714, 4722, 4730, 4738, 4751, 4759) -> ItemCategory.ARMOR_LEGS
                itemId in listOf(4710, 4718, 4726, 4734, 4747, 4755) -> ItemCategory.WEAPON_STAFF
                else -> ItemCategory.MISC
            }

            // Default fallback
            else -> ItemCategory.MISC
        }
    }

    /**
     * Check if we have sprite data for an item
     */
    fun hasItemData(itemId: Int): Boolean = itemCategoryMap.containsKey(itemId)
}

/**
 * Composable that renders an item sprite
 * Attempts to load real sprite first, falls back to vector rendering
 *
 * Loading priority:
 * 1. Local sprite from cache (if mapped)
 * 2. External sprite from URL (if mapped, e.g., OSRS Wiki)
 * 3. Vector fallback rendering
 */
@Composable
fun ItemSprite(
    itemId: Int,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    showBackground: Boolean = false,
    enableExternalSprites: Boolean = true
) {
    val spriteLoader = LocalSpriteLoader.current
    // Check cache first
    val cachedBitmap = remember(itemId) { ItemSpriteMapping.getCachedSprite(itemId) }
    var bitmap by remember(itemId) { mutableStateOf<ImageBitmap?>(cachedBitmap) }
    var loadState by remember(itemId) {
        mutableStateOf(
            when {
                cachedBitmap != null -> ItemSpriteLoadState.LOADED
                ItemSpriteMapping.hasFailed(itemId) -> ItemSpriteLoadState.FALLBACK
                ItemSpriteMapping.isLoading(itemId) -> ItemSpriteLoadState.LOADING
                else -> ItemSpriteLoadState.NOT_STARTED
            }
        )
    }

    // Get sprite reference from mapping
    val spriteRef = remember(itemId) { ItemSpriteMapping.getSpriteRef(itemId) }

    // Try to load sprite based on source
    LaunchedEffect(itemId, spriteLoader) {
        // Skip if already loaded, failed, or currently loading
        if (loadState != ItemSpriteLoadState.NOT_STARTED) return@LaunchedEffect

        when (spriteRef.source) {
            ItemSpriteMapping.SpriteSource.LOCAL_SPRITE -> {
                // Load from local sprite cache
                spriteRef.spriteId?.let { spriteId ->
                    if (spriteLoader != null) {
                        loadState = ItemSpriteLoadState.LOADING
                        ItemSpriteMapping.markLoading(itemId)
                        try {
                            val loaded = spriteLoader.loadSprite(spriteId)
                            if (loaded != null) {
                                ItemSpriteMapping.cacheSprite(itemId, loaded)
                                bitmap = loaded
                                loadState = ItemSpriteLoadState.LOADED
                            } else {
                                ItemSpriteMapping.markFailed(itemId)
                                loadState = ItemSpriteLoadState.FALLBACK
                            }
                        } catch (e: Exception) {
                            println("[ItemSprite] Failed to load local sprite for item $itemId: ${e.message}")
                            ItemSpriteMapping.markFailed(itemId)
                            loadState = ItemSpriteLoadState.FALLBACK
                        }
                    } else {
                        loadState = ItemSpriteLoadState.FALLBACK
                    }
                } ?: run {
                    loadState = ItemSpriteLoadState.FALLBACK
                }
            }

            ItemSpriteMapping.SpriteSource.EXTERNAL_URL -> {
                // External sprites disabled or no URL or no loader
                if (!enableExternalSprites || spriteRef.url == null || spriteLoader == null) {
                    loadState = ItemSpriteLoadState.FALLBACK
                } else {
                    loadState = ItemSpriteLoadState.LOADING
                    ItemSpriteMapping.markLoading(itemId)
                    try {
                        val loaded = spriteLoader.loadFromUrl(spriteRef.url)
                        if (loaded != null) {
                            ItemSpriteMapping.cacheSprite(itemId, loaded)
                            bitmap = loaded
                            loadState = ItemSpriteLoadState.LOADED
                        } else {
                            ItemSpriteMapping.markFailed(itemId)
                            loadState = ItemSpriteLoadState.FALLBACK
                        }
                    } catch (e: Exception) {
                        println("[ItemSprite] Failed to load external sprite for item $itemId: ${e.message}")
                        ItemSpriteMapping.markFailed(itemId)
                        loadState = ItemSpriteLoadState.FALLBACK
                    }
                }
            }

            ItemSpriteMapping.SpriteSource.VECTOR_FALLBACK,
            ItemSpriteMapping.SpriteSource.MODEL_PLACEHOLDER -> {
                loadState = ItemSpriteLoadState.FALLBACK
            }
        }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null && loadState == ItemSpriteLoadState.LOADED -> {
                // Render real sprite
                Canvas(modifier = Modifier.size(size)) {
                    drawImage(
                        image = bitmap!!,
                        dstSize = IntSize(size.toPx().toInt(), size.toPx().toInt())
                    )
                }
            }

            loadState == ItemSpriteLoadState.LOADING -> {
                // Show loading placeholder (simple colored box)
                Canvas(modifier = Modifier.size(size)) {
                    drawRect(
                        color = Color(0x40808080),
                        size = this.size
                    )
                }
            }

            else -> {
                // Render vector fallback
                VectorItemSprite(
                    itemId = itemId,
                    size = size,
                    showBackground = showBackground,
                    category = spriteRef.fallbackCategory,
                    material = spriteRef.fallbackMaterial
                )
            }
        }
    }
}

/**
 * Item sprite loading states
 */
private enum class ItemSpriteLoadState {
    NOT_STARTED,
    LOADING,
    LOADED,
    FALLBACK
}

/**
 * Vector-based item sprite fallback
 */
@Composable
private fun VectorItemSprite(
    itemId: Int,
    size: Dp,
    showBackground: Boolean,
    category: ItemSpriteProvider.ItemCategory? = null,
    material: ItemSpriteProvider.MaterialType? = null
) {
    val info = ItemSpriteProvider.getItemInfo(itemId)
    val resolvedCategory = category ?: info?.category ?: ItemSpriteProvider.getCategoryForItem(itemId)
    val resolvedMaterial = material ?: info?.material ?: ItemSpriteProvider.getMaterialForItem(itemId)

    Canvas(
        modifier = Modifier
            .size(size)
            .then(
                if (showBackground) {
                    Modifier.background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF4A3828),
                                Color(0xFF2A1808)
                            )
                        )
                    )
                } else Modifier
            )
    ) {
        drawItemByCategory(resolvedCategory, resolvedMaterial, this.size)
    }
}

/**
 * Draw item sprite based on category
 */
private fun DrawScope.drawItemByCategory(
    category: ItemSpriteProvider.ItemCategory,
    material: ItemSpriteProvider.MaterialType,
    canvasSize: Size
) {
    val primary = material.primaryColor
    val secondary = material.secondaryColor
    val highlight = material.highlightColor
    val padding = canvasSize.minDimension * 0.1f
    val itemSize = canvasSize.minDimension - padding * 2
    val centerX = canvasSize.width / 2
    val centerY = canvasSize.height / 2

    when (category) {
        ItemSpriteProvider.ItemCategory.WEAPON_SWORD,
        ItemSpriteProvider.ItemCategory.WEAPON_DAGGER -> {
            drawSword(centerX, centerY, itemSize, primary, secondary, highlight)
        }

        ItemSpriteProvider.ItemCategory.WEAPON_AXE,
        ItemSpriteProvider.ItemCategory.TOOL_HATCHET -> {
            drawAxe(centerX, centerY, itemSize, primary, secondary, highlight)
        }

        ItemSpriteProvider.ItemCategory.WEAPON_BOW -> {
            drawBow(centerX, centerY, itemSize, primary, secondary)
        }

        ItemSpriteProvider.ItemCategory.WEAPON_STAFF -> {
            drawStaff(centerX, centerY, itemSize, primary, secondary)
        }

        ItemSpriteProvider.ItemCategory.WEAPON_MACE -> {
            drawMace(centerX, centerY, itemSize, primary, secondary, highlight)
        }

        ItemSpriteProvider.ItemCategory.ARMOR_HELMET -> {
            drawHelmet(centerX, centerY, itemSize, primary, secondary, highlight)
        }

        ItemSpriteProvider.ItemCategory.ARMOR_BODY -> {
            drawBody(centerX, centerY, itemSize, primary, secondary, highlight)
        }

        ItemSpriteProvider.ItemCategory.ARMOR_LEGS -> {
            drawLegs(centerX, centerY, itemSize, primary, secondary, highlight)
        }

        ItemSpriteProvider.ItemCategory.ARMOR_SHIELD -> {
            drawShield(centerX, centerY, itemSize, primary, secondary, highlight)
        }

        ItemSpriteProvider.ItemCategory.ARMOR_BOOTS -> {
            drawBoots(centerX, centerY, itemSize, primary, secondary)
        }

        ItemSpriteProvider.ItemCategory.ARMOR_GLOVES -> {
            drawGloves(centerX, centerY, itemSize, primary, secondary)
        }

        ItemSpriteProvider.ItemCategory.ARMOR_CAPE -> {
            drawCape(centerX, centerY, itemSize, primary, secondary)
        }

        ItemSpriteProvider.ItemCategory.ARMOR_AMULET -> {
            drawAmulet(centerX, centerY, itemSize, primary, highlight)
        }

        ItemSpriteProvider.ItemCategory.ARMOR_RING -> {
            drawRing(centerX, centerY, itemSize, primary, highlight)
        }

        ItemSpriteProvider.ItemCategory.RESOURCE_ORE -> {
            drawOre(centerX, centerY, itemSize, primary, secondary, highlight)
        }

        ItemSpriteProvider.ItemCategory.RESOURCE_BAR -> {
            drawBar(centerX, centerY, itemSize, primary, secondary, highlight)
        }

        ItemSpriteProvider.ItemCategory.RESOURCE_LOG -> {
            drawLog(centerX, centerY, itemSize, primary, secondary)
        }

        ItemSpriteProvider.ItemCategory.RESOURCE_FISH_RAW,
        ItemSpriteProvider.ItemCategory.RESOURCE_FISH_COOKED -> {
            drawFish(centerX, centerY, itemSize, primary)
        }

        ItemSpriteProvider.ItemCategory.FOOD -> {
            drawFood(centerX, centerY, itemSize)
        }

        ItemSpriteProvider.ItemCategory.POTION -> {
            drawPotion(centerX, centerY, itemSize, primary)
        }

        ItemSpriteProvider.ItemCategory.RUNE -> {
            drawRune(centerX, centerY, itemSize, primary)
        }

        ItemSpriteProvider.ItemCategory.COIN -> {
            drawCoin(centerX, centerY, itemSize, primary, highlight)
        }

        ItemSpriteProvider.ItemCategory.GEM -> {
            drawGem(centerX, centerY, itemSize, primary, highlight)
        }

        ItemSpriteProvider.ItemCategory.BONE -> {
            drawBone(centerX, centerY, itemSize)
        }

        ItemSpriteProvider.ItemCategory.ARROW,
        ItemSpriteProvider.ItemCategory.BOLT -> {
            drawArrow(centerX, centerY, itemSize, primary)
        }

        ItemSpriteProvider.ItemCategory.TOOL_PICKAXE -> {
            drawPickaxe(centerX, centerY, itemSize, primary, secondary, highlight)
        }

        else -> {
            drawMisc(centerX, centerY, itemSize, primary)
        }
    }
}

// Drawing functions for each item type

private fun DrawScope.drawSword(cx: Float, cy: Float, size: Float, primary: Color, secondary: Color, highlight: Color) {
    val bladeWidth = size * 0.12f
    val bladeLength = size * 0.7f
    val handleLength = size * 0.25f

    // Blade
    val bladePath = Path().apply {
        moveTo(cx - bladeWidth / 2, cy + handleLength)
        lineTo(cx + bladeWidth / 2, cy + handleLength)
        lineTo(cx + bladeWidth / 3, cy - bladeLength)
        lineTo(cx, cy - bladeLength - size * 0.05f)
        lineTo(cx - bladeWidth / 3, cy - bladeLength)
        close()
    }
    drawPath(bladePath, primary)
    drawPath(bladePath, secondary, style = Stroke(1f))

    // Highlight
    drawLine(highlight, Offset(cx, cy - bladeLength), Offset(cx, cy + handleLength * 0.5f), strokeWidth = 1.5f)

    // Guard
    drawRect(secondary, Offset(cx - size * 0.15f, cy + handleLength - size * 0.05f), Size(size * 0.3f, size * 0.08f))

    // Handle
    drawRect(
        Color(0xFF4A3020),
        Offset(cx - bladeWidth / 3, cy + handleLength),
        Size(bladeWidth * 0.66f, handleLength * 0.8f)
    )

    // Pommel
    drawCircle(primary, size * 0.05f, Offset(cx, cy + handleLength + handleLength * 0.7f))
}

private fun DrawScope.drawAxe(cx: Float, cy: Float, size: Float, primary: Color, secondary: Color, highlight: Color) {
    // Handle
    drawRect(Color(0xFF6B4020), Offset(cx - size * 0.04f, cy - size * 0.1f), Size(size * 0.08f, size * 0.6f))

    // Axe head
    val headPath = Path().apply {
        moveTo(cx - size * 0.04f, cy - size * 0.35f)
        lineTo(cx - size * 0.25f, cy - size * 0.15f)
        lineTo(cx - size * 0.25f, cy + size * 0.05f)
        lineTo(cx - size * 0.04f, cy + size * 0.15f)
        close()
    }
    drawPath(headPath, primary)
    drawPath(headPath, secondary, style = Stroke(1f))

    // Highlight edge
    drawLine(
        highlight,
        Offset(cx - size * 0.25f, cy - size * 0.1f),
        Offset(cx - size * 0.25f, cy + size * 0.02f),
        strokeWidth = 2f
    )
}

private fun DrawScope.drawBow(cx: Float, cy: Float, size: Float, primary: Color, secondary: Color) {
    // Bow curve
    val bowPath = Path().apply {
        moveTo(cx - size * 0.15f, cy - size * 0.35f)
        quadraticBezierTo(cx - size * 0.35f, cy, cx - size * 0.15f, cy + size * 0.35f)
    }
    drawPath(bowPath, primary, style = Stroke(size * 0.06f))

    // String
    drawLine(
        Color(0xFFD2B48C),
        Offset(cx - size * 0.15f, cy - size * 0.35f),
        Offset(cx - size * 0.15f, cy + size * 0.35f),
        strokeWidth = 1.5f
    )
}

private fun DrawScope.drawStaff(cx: Float, cy: Float, size: Float, primary: Color, secondary: Color) {
    // Staff body
    drawRect(primary, Offset(cx - size * 0.04f, cy - size * 0.35f), Size(size * 0.08f, size * 0.7f))

    // Orb on top
    drawCircle(Color(0xFF4169E1), size * 0.12f, Offset(cx, cy - size * 0.35f))
    drawCircle(Color(0xFF6189FF), size * 0.06f, Offset(cx - size * 0.03f, cy - size * 0.38f))
}

private fun DrawScope.drawMace(cx: Float, cy: Float, size: Float, primary: Color, secondary: Color, highlight: Color) {
    // Handle
    drawRect(Color(0xFF6B4020), Offset(cx - size * 0.03f, cy), Size(size * 0.06f, size * 0.4f))

    // Mace head
    drawCircle(primary, size * 0.18f, Offset(cx, cy - size * 0.1f))
    drawCircle(highlight, size * 0.05f, Offset(cx - size * 0.08f, cy - size * 0.15f))
}

private fun DrawScope.drawHelmet(
    cx: Float,
    cy: Float,
    size: Float,
    primary: Color,
    secondary: Color,
    highlight: Color
) {
    // Main helmet shape
    val helmetPath = Path().apply {
        moveTo(cx - size * 0.3f, cy + size * 0.2f)
        lineTo(cx - size * 0.35f, cy - size * 0.1f)
        quadraticBezierTo(cx, cy - size * 0.4f, cx + size * 0.35f, cy - size * 0.1f)
        lineTo(cx + size * 0.3f, cy + size * 0.2f)
        close()
    }
    drawPath(helmetPath, primary)
    drawPath(helmetPath, secondary, style = Stroke(1f))

    // Face opening
    drawOval(
        Color.Black.copy(alpha = 0.5f),
        Offset(cx - size * 0.12f, cy - size * 0.05f),
        Size(size * 0.24f, size * 0.2f)
    )

    // Highlight
    drawLine(
        highlight,
        Offset(cx - size * 0.15f, cy - size * 0.25f),
        Offset(cx + size * 0.1f, cy - size * 0.3f),
        strokeWidth = 2f
    )
}

private fun DrawScope.drawBody(cx: Float, cy: Float, size: Float, primary: Color, secondary: Color, highlight: Color) {
    // Body shape
    val bodyPath = Path().apply {
        moveTo(cx - size * 0.25f, cy - size * 0.3f)
        lineTo(cx - size * 0.35f, cy - size * 0.1f)
        lineTo(cx - size * 0.25f, cy + size * 0.35f)
        lineTo(cx + size * 0.25f, cy + size * 0.35f)
        lineTo(cx + size * 0.35f, cy - size * 0.1f)
        lineTo(cx + size * 0.25f, cy - size * 0.3f)
        close()
    }
    drawPath(bodyPath, primary)
    drawPath(bodyPath, secondary, style = Stroke(1f))

    // Highlight
    drawLine(highlight, Offset(cx, cy - size * 0.25f), Offset(cx, cy + size * 0.2f), strokeWidth = 2f)
}

private fun DrawScope.drawLegs(cx: Float, cy: Float, size: Float, primary: Color, secondary: Color, highlight: Color) {
    // Left leg
    val leftLeg = Path().apply {
        moveTo(cx - size * 0.2f, cy - size * 0.35f)
        lineTo(cx - size * 0.25f, cy + size * 0.35f)
        lineTo(cx - size * 0.08f, cy + size * 0.35f)
        lineTo(cx - size * 0.05f, cy - size * 0.35f)
        close()
    }
    drawPath(leftLeg, primary)
    drawPath(leftLeg, secondary, style = Stroke(1f))

    // Right leg
    val rightLeg = Path().apply {
        moveTo(cx + size * 0.05f, cy - size * 0.35f)
        lineTo(cx + size * 0.08f, cy + size * 0.35f)
        lineTo(cx + size * 0.25f, cy + size * 0.35f)
        lineTo(cx + size * 0.2f, cy - size * 0.35f)
        close()
    }
    drawPath(rightLeg, primary)
    drawPath(rightLeg, secondary, style = Stroke(1f))
}

private fun DrawScope.drawShield(
    cx: Float,
    cy: Float,
    size: Float,
    primary: Color,
    secondary: Color,
    highlight: Color
) {
    val shieldPath = Path().apply {
        moveTo(cx, cy - size * 0.35f)
        lineTo(cx - size * 0.3f, cy - size * 0.2f)
        lineTo(cx - size * 0.3f, cy + size * 0.1f)
        lineTo(cx, cy + size * 0.35f)
        lineTo(cx + size * 0.3f, cy + size * 0.1f)
        lineTo(cx + size * 0.3f, cy - size * 0.2f)
        close()
    }
    drawPath(shieldPath, primary)
    drawPath(shieldPath, secondary, style = Stroke(2f))

    // Emblem
    drawCircle(highlight, size * 0.1f, Offset(cx, cy - size * 0.05f))
}

private fun DrawScope.drawBoots(cx: Float, cy: Float, size: Float, primary: Color, secondary: Color) {
    // Left boot
    drawOval(primary, Offset(cx - size * 0.25f, cy - size * 0.1f), Size(size * 0.2f, size * 0.35f))
    // Right boot
    drawOval(primary, Offset(cx + size * 0.05f, cy - size * 0.1f), Size(size * 0.2f, size * 0.35f))
}

private fun DrawScope.drawGloves(cx: Float, cy: Float, size: Float, primary: Color, secondary: Color) {
    // Left glove
    drawOval(primary, Offset(cx - size * 0.3f, cy - size * 0.15f), Size(size * 0.25f, size * 0.3f))
    // Right glove
    drawOval(primary, Offset(cx + size * 0.05f, cy - size * 0.15f), Size(size * 0.25f, size * 0.3f))
}

private fun DrawScope.drawCape(cx: Float, cy: Float, size: Float, primary: Color, secondary: Color) {
    val capePath = Path().apply {
        moveTo(cx - size * 0.25f, cy - size * 0.35f)
        lineTo(cx + size * 0.25f, cy - size * 0.35f)
        lineTo(cx + size * 0.3f, cy + size * 0.35f)
        lineTo(cx, cy + size * 0.25f)
        lineTo(cx - size * 0.3f, cy + size * 0.35f)
        close()
    }
    drawPath(capePath, primary)
    drawPath(capePath, secondary, style = Stroke(1f))
}

private fun DrawScope.drawAmulet(cx: Float, cy: Float, size: Float, primary: Color, highlight: Color) {
    // Chain
    drawArc(
        Color(0xFFD4A017),
        -30f,
        -120f,
        false,
        Offset(cx - size * 0.15f, cy - size * 0.35f),
        Size(size * 0.3f, size * 0.3f),
        style = Stroke(2f)
    )

    // Pendant
    drawOval(primary, Offset(cx - size * 0.1f, cy), Size(size * 0.2f, size * 0.25f))
    drawCircle(highlight, size * 0.05f, Offset(cx - size * 0.02f, cy + size * 0.08f))
}

private fun DrawScope.drawRing(cx: Float, cy: Float, size: Float, primary: Color, highlight: Color) {
    drawCircle(primary, size * 0.2f, Offset(cx, cy))
    drawCircle(Color.Black.copy(alpha = 0.3f), size * 0.12f, Offset(cx, cy))
    drawCircle(highlight, size * 0.05f, Offset(cx - size * 0.1f, cy - size * 0.08f))
}

private fun DrawScope.drawOre(cx: Float, cy: Float, size: Float, primary: Color, secondary: Color, highlight: Color) {
    // Rock shape
    val orePath = Path().apply {
        moveTo(cx, cy - size * 0.25f)
        lineTo(cx - size * 0.3f, cy)
        lineTo(cx - size * 0.2f, cy + size * 0.25f)
        lineTo(cx + size * 0.2f, cy + size * 0.25f)
        lineTo(cx + size * 0.3f, cy)
        close()
    }
    drawPath(orePath, Color(0xFF696969))
    drawPath(orePath, Color(0xFF505050), style = Stroke(1f))

    // Ore veins
    drawCircle(primary, size * 0.08f, Offset(cx - size * 0.1f, cy - size * 0.05f))
    drawCircle(primary, size * 0.06f, Offset(cx + size * 0.08f, cy + size * 0.08f))
    drawCircle(highlight, size * 0.03f, Offset(cx - size * 0.12f, cy - size * 0.08f))
}

private fun DrawScope.drawBar(cx: Float, cy: Float, size: Float, primary: Color, secondary: Color, highlight: Color) {
    // Bar shape (trapezoid)
    val barPath = Path().apply {
        moveTo(cx - size * 0.25f, cy - size * 0.1f)
        lineTo(cx + size * 0.25f, cy - size * 0.1f)
        lineTo(cx + size * 0.2f, cy + size * 0.15f)
        lineTo(cx - size * 0.2f, cy + size * 0.15f)
        close()
    }
    drawPath(barPath, primary)
    drawPath(barPath, secondary, style = Stroke(1f))

    // Highlight
    drawLine(
        highlight,
        Offset(cx - size * 0.2f, cy - size * 0.05f),
        Offset(cx + size * 0.15f, cy - size * 0.05f),
        strokeWidth = 2f
    )
}

private fun DrawScope.drawLog(cx: Float, cy: Float, size: Float, primary: Color, secondary: Color) {
    // Log body
    drawOval(primary, Offset(cx - size * 0.35f, cy - size * 0.1f), Size(size * 0.7f, size * 0.2f))

    // End grain circles
    drawCircle(secondary, size * 0.08f, Offset(cx - size * 0.3f, cy))
    drawCircle(Color(0xFF4A3020), size * 0.04f, Offset(cx - size * 0.3f, cy))
}

private fun DrawScope.drawFish(cx: Float, cy: Float, size: Float, color: Color) {
    // Fish body
    val fishPath = Path().apply {
        moveTo(cx - size * 0.3f, cy)
        quadraticBezierTo(cx, cy - size * 0.2f, cx + size * 0.2f, cy)
        quadraticBezierTo(cx, cy + size * 0.2f, cx - size * 0.3f, cy)
        close()
    }
    drawPath(fishPath, Color(0xFF87CEEB))

    // Tail
    val tailPath = Path().apply {
        moveTo(cx + size * 0.15f, cy)
        lineTo(cx + size * 0.35f, cy - size * 0.15f)
        lineTo(cx + size * 0.35f, cy + size * 0.15f)
        close()
    }
    drawPath(tailPath, Color(0xFF87CEEB))

    // Eye
    drawCircle(Color.Black, size * 0.03f, Offset(cx - size * 0.15f, cy - size * 0.05f))
}

private fun DrawScope.drawFood(cx: Float, cy: Float, size: Float) {
    // Bread/generic food shape
    drawOval(Color(0xFFDEB887), Offset(cx - size * 0.25f, cy - size * 0.15f), Size(size * 0.5f, size * 0.3f))
    drawArc(
        Color(0xFFC4A76C),
        0f,
        180f,
        true,
        Offset(cx - size * 0.2f, cy - size * 0.1f),
        Size(size * 0.4f, size * 0.15f)
    )
}

private fun DrawScope.drawPotion(cx: Float, cy: Float, size: Float, liquidColor: Color) {
    // Flask shape
    val flaskPath = Path().apply {
        moveTo(cx - size * 0.08f, cy - size * 0.35f)
        lineTo(cx - size * 0.08f, cy - size * 0.15f)
        lineTo(cx - size * 0.2f, cy + size * 0.25f)
        quadraticBezierTo(cx, cy + size * 0.35f, cx + size * 0.2f, cy + size * 0.25f)
        lineTo(cx + size * 0.08f, cy - size * 0.15f)
        lineTo(cx + size * 0.08f, cy - size * 0.35f)
        close()
    }
    drawPath(flaskPath, Color(0xFF87CEEB).copy(alpha = 0.5f))
    drawPath(flaskPath, Color(0xFF5F9EA0), style = Stroke(1.5f))

    // Liquid
    val liquidPath = Path().apply {
        moveTo(cx - size * 0.15f, cy)
        lineTo(cx - size * 0.18f, cy + size * 0.2f)
        quadraticBezierTo(cx, cy + size * 0.3f, cx + size * 0.18f, cy + size * 0.2f)
        lineTo(cx + size * 0.15f, cy)
        close()
    }
    drawPath(liquidPath, liquidColor)
}

private fun DrawScope.drawRune(cx: Float, cy: Float, size: Float, color: Color) {
    // Rune stone
    drawCircle(Color(0xFF2F2F2F), size * 0.28f, Offset(cx, cy))
    drawCircle(Color(0xFF3F3F3F), size * 0.22f, Offset(cx, cy))

    // Symbol
    drawLine(color, Offset(cx, cy - size * 0.12f), Offset(cx, cy + size * 0.12f), strokeWidth = 2f)
    drawLine(
        color,
        Offset(cx - size * 0.1f, cy - size * 0.05f),
        Offset(cx + size * 0.1f, cy + size * 0.05f),
        strokeWidth = 2f
    )
}

private fun DrawScope.drawCoin(cx: Float, cy: Float, size: Float, primary: Color, highlight: Color) {
    drawCircle(primary, size * 0.25f, Offset(cx, cy))
    drawCircle(highlight, size * 0.08f, Offset(cx - size * 0.08f, cy - size * 0.08f))
}

private fun DrawScope.drawGem(cx: Float, cy: Float, size: Float, primary: Color, highlight: Color) {
    // Diamond shape
    val gemPath = Path().apply {
        moveTo(cx, cy - size * 0.25f)
        lineTo(cx - size * 0.2f, cy)
        lineTo(cx, cy + size * 0.25f)
        lineTo(cx + size * 0.2f, cy)
        close()
    }
    drawPath(gemPath, primary)
    drawPath(gemPath, highlight.copy(alpha = 0.5f), style = Stroke(1f))

    // Sparkle
    drawCircle(highlight, size * 0.04f, Offset(cx - size * 0.08f, cy - size * 0.08f))
}

private fun DrawScope.drawBone(cx: Float, cy: Float, size: Float) {
    // Bone shape
    drawOval(Color(0xFFF5F5DC), Offset(cx - size * 0.05f, cy - size * 0.25f), Size(size * 0.1f, size * 0.5f))

    // Bone ends
    drawCircle(Color(0xFFF5F5DC), size * 0.08f, Offset(cx, cy - size * 0.25f))
    drawCircle(Color(0xFFF5F5DC), size * 0.08f, Offset(cx, cy + size * 0.25f))
}

private fun DrawScope.drawArrow(cx: Float, cy: Float, size: Float, headColor: Color) {
    // Shaft
    drawLine(Color(0xFF8B4513), Offset(cx, cy + size * 0.35f), Offset(cx, cy - size * 0.2f), strokeWidth = 2f)

    // Head
    val headPath = Path().apply {
        moveTo(cx, cy - size * 0.35f)
        lineTo(cx - size * 0.08f, cy - size * 0.15f)
        lineTo(cx + size * 0.08f, cy - size * 0.15f)
        close()
    }
    drawPath(headPath, headColor)

    // Fletching
    drawLine(
        Color(0xFFD2B48C),
        Offset(cx, cy + size * 0.35f),
        Offset(cx - size * 0.1f, cy + size * 0.25f),
        strokeWidth = 1.5f
    )
    drawLine(
        Color(0xFFD2B48C),
        Offset(cx, cy + size * 0.35f),
        Offset(cx + size * 0.1f, cy + size * 0.25f),
        strokeWidth = 1.5f
    )
}

private fun DrawScope.drawPickaxe(
    cx: Float,
    cy: Float,
    size: Float,
    primary: Color,
    secondary: Color,
    highlight: Color
) {
    // Handle
    drawRect(Color(0xFF6B4020), Offset(cx - size * 0.03f, cy - size * 0.05f), Size(size * 0.06f, size * 0.45f))

    // Pick head
    val pickPath = Path().apply {
        moveTo(cx - size * 0.3f, cy - size * 0.25f)
        lineTo(cx, cy - size * 0.1f)
        lineTo(cx + size * 0.3f, cy - size * 0.25f)
        lineTo(cx + size * 0.25f, cy - size * 0.15f)
        lineTo(cx, cy - size * 0.05f)
        lineTo(cx - size * 0.25f, cy - size * 0.15f)
        close()
    }
    drawPath(pickPath, primary)
    drawPath(pickPath, secondary, style = Stroke(1f))

    // Highlight
    drawLine(
        highlight,
        Offset(cx - size * 0.25f, cy - size * 0.2f),
        Offset(cx - size * 0.1f, cy - size * 0.12f),
        strokeWidth = 1.5f
    )
}

private fun DrawScope.drawMisc(cx: Float, cy: Float, size: Float, color: Color) {
    // Generic bag/pouch shape
    val bagPath = Path().apply {
        moveTo(cx - size * 0.2f, cy - size * 0.15f)
        quadraticBezierTo(cx - size * 0.25f, cy + size * 0.2f, cx, cy + size * 0.25f)
        quadraticBezierTo(cx + size * 0.25f, cy + size * 0.2f, cx + size * 0.2f, cy - size * 0.15f)
        lineTo(cx + size * 0.15f, cy - size * 0.25f)
        lineTo(cx - size * 0.15f, cy - size * 0.25f)
        close()
    }
    drawPath(bagPath, color)
    drawPath(bagPath, color.copy(alpha = 0.5f), style = Stroke(1f))
}
