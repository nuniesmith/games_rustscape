package com.rustscape.client.ui.components

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Item to Sprite ID Mapping
 *
 * Maps RuneScape item IDs to their corresponding sprite resources.
 *
 * Since RS item sprites are typically rendered from 3D models at runtime,
 * this mapping provides:
 * 1. Direct sprite IDs for items with pre-extracted sprites
 * 2. Category-based fallback for items without specific sprites
 * 3. Support for external sprite sources (e.g., OSRS Wiki)
 */
object ItemSpriteMapping {

    /**
     * Sprite source types
     */
    enum class SpriteSource {
        /** Local sprite from assets/rendering/sprites/{id}.png */
        LOCAL_SPRITE,

        /** External URL (e.g., OSRS Wiki) */
        EXTERNAL_URL,

        /** Vector fallback rendering */
        VECTOR_FALLBACK,

        /** Item model placeholder */
        MODEL_PLACEHOLDER
    }

    /**
     * Sprite reference containing source and identifier
     */
    data class SpriteRef(
        val source: SpriteSource,
        val spriteId: Int? = null,
        val url: String? = null,
        val fallbackCategory: ItemSpriteProvider.ItemCategory? = null,
        val fallbackMaterial: ItemSpriteProvider.MaterialType? = null
    )

    /**
     * Known item ID to local sprite ID mappings
     * These are items where we have a matching sprite in the cache
     *
     * Note: Many of these are UI/skill sprites, not actual item sprites.
     * Item sprites in RS are rendered from 3D models.
     */
    private val localSpriteMappings = mapOf<Int, Int>(
        // Currently no direct item->sprite mappings as cache sprites are mostly UI elements
        // This would be populated if we had extracted item sprites
    )

    /**
     * Item IDs that can use external sprite URLs (OSRS Wiki format)
     * Format: https://oldschool.runescape.wiki/images/{ItemName}_detail.png
     */
    private val wikiSpriteNames = mapOf(
        // Coins
        995 to "Coins_10000",

        // Common resources
        1511 to "Logs",
        1521 to "Oak_logs",
        1519 to "Willow_logs",
        1517 to "Maple_logs",
        1515 to "Yew_logs",
        1513 to "Magic_logs",

        // Ores
        436 to "Copper_ore",
        438 to "Tin_ore",
        440 to "Iron_ore",
        442 to "Silver_ore",
        444 to "Gold_ore",
        447 to "Mithril_ore",
        449 to "Adamantite_ore",
        451 to "Runite_ore",
        453 to "Coal",

        // Bars
        2349 to "Bronze_bar",
        2351 to "Iron_bar",
        2353 to "Steel_bar",
        2355 to "Silver_bar",
        2357 to "Gold_bar",
        2359 to "Mithril_bar",
        2361 to "Adamantite_bar",
        2363 to "Runite_bar",

        // Fish (raw)
        317 to "Raw_shrimps",
        327 to "Raw_sardine",
        321 to "Raw_anchovies",
        335 to "Raw_trout",
        331 to "Raw_salmon",
        359 to "Raw_tuna",
        371 to "Raw_swordfish",
        383 to "Raw_shark",

        // Fish (cooked)
        315 to "Shrimps",
        325 to "Sardine",
        319 to "Anchovies",
        333 to "Trout",
        329 to "Salmon",
        361 to "Tuna",
        373 to "Swordfish",
        385 to "Shark",

        // Bones
        526 to "Bones",
        532 to "Big_bones",
        536 to "Dragon_bones",

        // Bronze weapons
        1277 to "Bronze_sword",
        1291 to "Bronze_longsword",
        1307 to "Bronze_scimitar",
        1321 to "Bronze_2h_sword",
        1205 to "Bronze_dagger",
        1351 to "Bronze_axe",
        1265 to "Bronze_pickaxe",
        1375 to "Bronze_battleaxe",
        1337 to "Bronze_mace",

        // Iron weapons
        1279 to "Iron_sword",
        1293 to "Iron_longsword",
        1309 to "Iron_scimitar",
        1323 to "Iron_2h_sword",
        1203 to "Iron_dagger",
        1349 to "Iron_axe",
        1267 to "Iron_pickaxe",
        1363 to "Iron_battleaxe",
        1335 to "Iron_mace",

        // Steel weapons
        1281 to "Steel_sword",
        1295 to "Steel_longsword",
        1311 to "Steel_scimitar",
        1325 to "Steel_2h_sword",
        1207 to "Steel_dagger",
        1353 to "Steel_axe",
        1269 to "Steel_pickaxe",
        1365 to "Steel_battleaxe",
        1339 to "Steel_mace",

        // Mithril weapons
        1285 to "Mithril_sword",
        1299 to "Mithril_longsword",
        1315 to "Mithril_scimitar",
        1329 to "Mithril_2h_sword",
        1209 to "Mithril_dagger",
        1355 to "Mithril_axe",
        1273 to "Mithril_pickaxe",
        1369 to "Mithril_battleaxe",
        1343 to "Mithril_mace",

        // Adamant weapons
        1287 to "Adamant_sword",
        1301 to "Adamant_longsword",
        1317 to "Adamant_scimitar",
        1331 to "Adamant_2h_sword",
        1211 to "Adamant_dagger",
        1357 to "Adamant_axe",
        1271 to "Adamant_pickaxe",
        1371 to "Adamant_battleaxe",
        1345 to "Adamant_mace",

        // Rune weapons
        1289 to "Rune_sword",
        1303 to "Rune_longsword",
        1319 to "Rune_scimitar",
        1333 to "Rune_2h_sword",
        1213 to "Rune_dagger",
        1359 to "Rune_axe",
        1275 to "Rune_pickaxe",
        1373 to "Rune_battleaxe",
        1347 to "Rune_mace",

        // Dragon weapons
        1215 to "Dragon_dagger",
        1305 to "Dragon_longsword",
        4587 to "Dragon_scimitar",
        6739 to "Dragon_axe",

        // Bronze armor
        1117 to "Bronze_platebody",
        1075 to "Bronze_platelegs",
        1087 to "Bronze_plateskirt",
        1155 to "Bronze_full_helm",
        1139 to "Bronze_med_helm",
        1173 to "Bronze_sq_shield",
        1189 to "Bronze_kiteshield",

        // Iron armor
        1115 to "Iron_platebody",
        1067 to "Iron_platelegs",
        1081 to "Iron_plateskirt",
        1153 to "Iron_full_helm",
        1137 to "Iron_med_helm",
        1175 to "Iron_sq_shield",
        1191 to "Iron_kiteshield",

        // Steel armor
        1119 to "Steel_platebody",
        1069 to "Steel_platelegs",
        1083 to "Steel_plateskirt",
        1157 to "Steel_full_helm",
        1141 to "Steel_med_helm",
        1177 to "Steel_sq_shield",
        1193 to "Steel_kiteshield",

        // Mithril armor
        1121 to "Mithril_platebody",
        1071 to "Mithril_platelegs",
        1085 to "Mithril_plateskirt",
        1159 to "Mithril_full_helm",
        1143 to "Mithril_med_helm",
        1181 to "Mithril_sq_shield",
        1197 to "Mithril_kiteshield",

        // Adamant armor
        1123 to "Adamant_platebody",
        1073 to "Adamant_platelegs",
        1091 to "Adamant_plateskirt",
        1161 to "Adamant_full_helm",
        1145 to "Adamant_med_helm",
        1183 to "Adamant_sq_shield",
        1199 to "Adamant_kiteshield",

        // Rune armor
        1127 to "Rune_platebody",
        1079 to "Rune_platelegs",
        1093 to "Rune_plateskirt",
        1163 to "Rune_full_helm",
        1147 to "Rune_med_helm",
        1185 to "Rune_sq_shield",
        1201 to "Rune_kiteshield",

        // Shields
        1171 to "Wooden_shield",

        // Bows
        841 to "Shortbow",
        839 to "Longbow",
        843 to "Oak_shortbow",
        845 to "Oak_longbow",
        849 to "Willow_shortbow",
        847 to "Willow_longbow",
        853 to "Maple_shortbow",
        851 to "Maple_longbow",
        857 to "Yew_shortbow",
        855 to "Yew_longbow",
        861 to "Magic_shortbow",
        859 to "Magic_longbow",

        // Arrows
        882 to "Bronze_arrow",
        884 to "Iron_arrow",
        886 to "Steel_arrow",
        888 to "Mithril_arrow",
        890 to "Adamant_arrow",
        892 to "Rune_arrow",

        // Runes
        554 to "Fire_rune",
        555 to "Water_rune",
        556 to "Air_rune",
        557 to "Earth_rune",
        558 to "Mind_rune",
        559 to "Body_rune",
        560 to "Death_rune",
        561 to "Nature_rune",
        562 to "Chaos_rune",
        563 to "Law_rune",
        564 to "Cosmic_rune",
        565 to "Blood_rune",
        566 to "Soul_rune",

        // Potions
        2428 to "Attack_potion(4)",
        2432 to "Defence_potion(4)",
        2436 to "Strength_potion(4)",
        2440 to "Ranging_potion(4)",
        2444 to "Magic_potion(4)",
        2448 to "Antifire_potion(4)",
        2452 to "Antipoison(4)",
        3024 to "Super_restore(4)",
        6685 to "Saradomin_brew(4)",

        // Food
        1965 to "Cabbage",
        1971 to "Kebab",
        2142 to "Meat_pie",
        2289 to "Anchovy_pizza",
        2293 to "Meat_pizza",
        7946 to "Monkfish",

        // Tools
        946 to "Knife",
        590 to "Tinderbox",
        1755 to "Chisel",
        2347 to "Hammer",
        1733 to "Needle",
        1734 to "Thread",

        // Other common items
        1925 to "Bucket",
        1929 to "Bucket_of_water",
        1931 to "Pot",
        1935 to "Jug",
        1937 to "Jug_of_water",
        1942 to "Potato",
        1944 to "Egg",
        1951 to "Onion",
        1957 to "Flour",
        1959 to "Bread",
        1985 to "Stew",
        1987 to "Plain_pizza",
        2309 to "Bread_dough",
        2307 to "Pastry_dough",

        // Capes
        1007 to "Red_cape",
        1019 to "Black_cape",
        1021 to "Blue_cape",
        1023 to "Yellow_cape",
        1027 to "Green_cape",
        1052 to "Cape_of_legends",
        2412 to "Saradomin_cape",
        2413 to "Zamorak_cape",
        2414 to "Guthix_cape",
        6570 to "Fire_cape",
        9747 to "Attack_cape",
        9753 to "Defence_cape",
        9750 to "Strength_cape",
        9768 to "Hitpoints_cape",
        9756 to "Ranging_cape",
        9762 to "Prayer_cape",
        9765 to "Magic_cape",
        9771 to "Cooking_cape",
        9774 to "Woodcutting_cape",
        9777 to "Fletching_cape",
        9780 to "Fishing_cape",
        9783 to "Firemaking_cape",
        9786 to "Crafting_cape",
        9789 to "Smithing_cape",
        9792 to "Mining_cape",
        9795 to "Herblore_cape",
        9798 to "Agility_cape",
        9801 to "Thieving_cape",
        9804 to "Slayer_cape",
        9807 to "Farming_cape",
        9810 to "Runecrafting_cape",
        9813 to "Quest_point_cape",

        // Jewelry - Amulets
        1694 to "Amulet_of_magic",
        1700 to "Amulet_of_defence",
        1706 to "Amulet_of_glory",
        1712 to "Amulet_of_glory(4)",
        1718 to "Amulet_of_power",
        1724 to "Amulet_of_strength",
        1731 to "Amulet_of_accuracy",
        4081 to "Amulet_of_fury",
        6585 to "Amulet_of_fury",

        // Jewelry - Rings
        2550 to "Ring_of_recoil",
        2552 to "Ring_of_dueling(8)",
        2568 to "Ring_of_forging",
        2570 to "Ring_of_life",
        6465 to "Ring_of_wealth",
        11773 to "Berserker_ring",
        11771 to "Archers_ring",
        11770 to "Seers_ring",
        11772 to "Warrior_ring",

        // Herbs (grimy)
        199 to "Grimy_guam_leaf",
        201 to "Grimy_marrentill",
        203 to "Grimy_tarromin",
        205 to "Grimy_harralander",
        207 to "Grimy_ranarr_weed",
        209 to "Grimy_irit_leaf",
        211 to "Grimy_avantoe",
        213 to "Grimy_kwuarm",
        215 to "Grimy_cadantine",
        217 to "Grimy_dwarf_weed",
        219 to "Grimy_torstol",
        2485 to "Grimy_lantadyme",
        3049 to "Grimy_toadflax",
        3051 to "Grimy_snapdragon",

        // Herbs (clean)
        249 to "Guam_leaf",
        251 to "Marrentill",
        253 to "Tarromin",
        255 to "Harralander",
        257 to "Ranarr_weed",
        259 to "Irit_leaf",
        261 to "Avantoe",
        263 to "Kwuarm",
        265 to "Cadantine",
        267 to "Dwarf_weed",
        269 to "Torstol",
        2481 to "Lantadyme",
        3002 to "Toadflax",
        3000 to "Snapdragon",

        // Seeds
        5291 to "Guam_seed",
        5292 to "Marrentill_seed",
        5293 to "Tarromin_seed",
        5294 to "Harralander_seed",
        5295 to "Ranarr_seed",
        5296 to "Toadflax_seed",
        5297 to "Irit_seed",
        5298 to "Avantoe_seed",
        5299 to "Kwuarm_seed",
        5300 to "Snapdragon_seed",
        5301 to "Cadantine_seed",
        5302 to "Lantadyme_seed",
        5303 to "Dwarf_weed_seed",
        5304 to "Torstol_seed",
        5312 to "Potato_seed",
        5313 to "Onion_seed",
        5314 to "Cabbage_seed",
        5315 to "Tomato_seed",
        5316 to "Sweetcorn_seed",
        5317 to "Strawberry_seed",
        5318 to "Watermelon_seed",
        5319 to "Barley_seed",
        5320 to "Hammerstone_seed",
        5321 to "Asgarnian_seed",
        5322 to "Jute_seed",
        5323 to "Yanillian_seed",
        5324 to "Krandorian_seed",
        5325 to "Wildblood_seed",
        5283 to "Acorn",
        5284 to "Willow_seed",
        5285 to "Maple_seed",
        5286 to "Yew_seed",
        5287 to "Magic_seed",
        5288 to "Apple_tree_seed",
        5289 to "Banana_tree_seed",
        5290 to "Orange_tree_seed",

        // More potions (all doses)
        2430 to "Attack_potion(3)",
        2429 to "Attack_potion(2)",
        2431 to "Attack_potion(1)",
        2434 to "Defence_potion(3)",
        2433 to "Defence_potion(2)",
        2435 to "Defence_potion(1)",
        2438 to "Strength_potion(3)",
        2437 to "Strength_potion(2)",
        2439 to "Strength_potion(1)",
        3026 to "Super_restore(3)",
        3028 to "Super_restore(2)",
        3030 to "Super_restore(1)",
        2442 to "Ranging_potion(3)",
        2444 to "Ranging_potion(2)",
        2446 to "Ranging_potion(1)",
        113 to "Super_strength(4)",
        115 to "Super_strength(3)",
        117 to "Super_strength(2)",
        119 to "Super_strength(1)",
        2436 to "Super_attack(4)",
        145 to "Super_attack(3)",
        147 to "Super_attack(2)",
        149 to "Super_attack(1)",
        163 to "Super_defence(4)",
        165 to "Super_defence(3)",
        167 to "Super_defence(2)",
        169 to "Super_defence(1)",
        6687 to "Saradomin_brew(3)",
        6689 to "Saradomin_brew(2)",
        6691 to "Saradomin_brew(1)",
        2454 to "Antipoison(3)",
        2456 to "Antipoison(2)",
        2458 to "Antipoison(1)",
        3040 to "Magic_potion(4)",
        3042 to "Magic_potion(3)",
        3044 to "Magic_potion(2)",
        3046 to "Magic_potion(1)",
        12695 to "Super_combat_potion(4)",
        12697 to "Super_combat_potion(3)",
        12699 to "Super_combat_potion(2)",
        12701 to "Super_combat_potion(1)",

        // Gloves/vambraces
        1059 to "Leather_gloves",
        1063 to "Leather_vambraces",
        1065 to "Green_d'hide_vambraces",
        2487 to "Blue_d'hide_vambraces",
        2489 to "Red_d'hide_vambraces",
        2491 to "Black_d'hide_vambraces",
        7453 to "Bronze_gloves",
        7454 to "Iron_gloves",
        7455 to "Steel_gloves",
        7456 to "Black_gloves",
        7457 to "Mithril_gloves",
        7458 to "Adamant_gloves",
        7459 to "Rune_gloves",
        7460 to "Dragon_gloves",
        7461 to "Barrows_gloves",

        // Boots
        1061 to "Leather_boots",
        4119 to "Climbing_boots",
        2577 to "Ranger_boots",
        3105 to "Climbing_boots",
        11840 to "Dragon_boots",

        // More food
        379 to "Lobster",
        1963 to "Banana",
        365 to "Bass",
        339 to "Cod",
        397 to "Sea_turtle",
        391 to "Manta_ray",
        7056 to "Cooked_karambwan",
        3144 to "Cooked_karambwan",
        6705 to "Potato_with_cheese",
        6709 to "Chilli_potato",

        // Staffs
        1379 to "Staff_of_air",
        1381 to "Staff_of_water",
        1383 to "Staff_of_earth",
        1385 to "Staff_of_fire",
        1387 to "Battlestaff",
        1389 to "Air_battlestaff",
        1391 to "Water_battlestaff",
        1393 to "Earth_battlestaff",
        1395 to "Fire_battlestaff",
        1397 to "Mystic_air_staff",
        1399 to "Mystic_water_staff",
        1401 to "Mystic_earth_staff",
        1403 to "Mystic_fire_staff",
        4675 to "Ancient_staff",
        4710 to "Ahrim's_staff",
        6562 to "Mud_battlestaff",
        6563 to "Mystic_mud_staff",
        11789 to "Staff_of_the_dead",

        // Barrows equipment
        4708 to "Ahrim's_hood",
        4712 to "Ahrim's_robetop",
        4714 to "Ahrim's_robeskirt",
        4716 to "Dharok's_helm",
        4718 to "Dharok's_greataxe",
        4720 to "Dharok's_platebody",
        4722 to "Dharok's_platelegs",
        4724 to "Guthan's_helm",
        4726 to "Guthan's_warspear",
        4728 to "Guthan's_platebody",
        4730 to "Guthan's_chainskirt",
        4732 to "Karil's_coif",
        4734 to "Karil's_crossbow",
        4736 to "Karil's_leathertop",
        4738 to "Karil's_leatherskirt",
        4745 to "Torag's_helm",
        4747 to "Torag's_hammers",
        4749 to "Torag's_platebody",
        4751 to "Torag's_platelegs",
        4753 to "Verac's_helm",
        4755 to "Verac's_flail",
        4757 to "Verac's_brassard",
        4759 to "Verac's_plateskirt",

        // God equipment
        2653 to "Guthix_full_helm",
        2655 to "Guthix_platebody",
        2657 to "Guthix_platelegs",
        2659 to "Guthix_plateskirt",
        2661 to "Guthix_kiteshield",
        2665 to "Saradomin_full_helm",
        2667 to "Saradomin_platebody",
        2669 to "Saradomin_platelegs",
        2671 to "Saradomin_plateskirt",
        2673 to "Saradomin_kiteshield",
        2675 to "Zamorak_full_helm",
        2677 to "Zamorak_platebody",
        2679 to "Zamorak_platelegs",
        2681 to "Zamorak_plateskirt",
        2683 to "Zamorak_kiteshield",

        // Dragonhide armour
        1129 to "Leather_body",
        1095 to "Leather_chaps",
        1131 to "Hardleather_body",
        1135 to "Green_d'hide_body",
        1099 to "Green_d'hide_chaps",
        2499 to "Blue_d'hide_body",
        2493 to "Blue_d'hide_chaps",
        2501 to "Red_d'hide_body",
        2495 to "Red_d'hide_chaps",
        2503 to "Black_d'hide_body",
        2497 to "Black_d'hide_chaps",

        // Crossbows
        9174 to "Bronze_crossbow",
        9176 to "Iron_crossbow",
        9177 to "Steel_crossbow",
        9179 to "Mithril_crossbow",
        9181 to "Adamant_crossbow",
        9183 to "Rune_crossbow",
        11785 to "Armadyl_crossbow",

        // Bolts
        877 to "Bronze_bolts",
        9140 to "Iron_bolts",
        9141 to "Steel_bolts",
        9142 to "Mithril_bolts",
        9143 to "Adamant_bolts",
        9144 to "Runite_bolts",
        9145 to "Dragon_bolts",

        // Miscellaneous useful items
        952 to "Spade",
        954 to "Rope",
        233 to "Pestle_and_mortar",
        1785 to "Glassblowing_pipe",
        5341 to "Rake",
        5343 to "Seed_dibber",
        5329 to "Secateurs",
        5331 to "Gardening_trowel",
        5325 to "Watering_can(8)",
        4031 to "Compost",
        6032 to "Supercompost",
        8778 to "Oak_plank",
        8780 to "Teak_plank",
        8782 to "Mahogany_plank",
        960 to "Plank",
        2150 to "Swamp_tar",
        7936 to "Pure_essence",
        1436 to "Rune_essence",
        5509 to "Small_pouch",
        5510 to "Medium_pouch",
        5512 to "Large_pouch",
        5514 to "Giant_pouch"
    )

    /**
     * Get sprite reference for an item ID
     */
    fun getSpriteRef(itemId: Int): SpriteRef {
        // Check local sprite mappings first
        localSpriteMappings[itemId]?.let { spriteId ->
            return SpriteRef(
                source = SpriteSource.LOCAL_SPRITE,
                spriteId = spriteId
            )
        }

        // Check if we have a wiki sprite name
        wikiSpriteNames[itemId]?.let { name ->
            return SpriteRef(
                source = SpriteSource.EXTERNAL_URL,
                url = getWikiSpriteUrl(name)
            )
        }

        // Fall back to vector rendering based on item info
        val info = ItemSpriteProvider.getItemInfo(itemId)
        return if (info != null) {
            SpriteRef(
                source = SpriteSource.VECTOR_FALLBACK,
                fallbackCategory = info.category,
                fallbackMaterial = info.material
            )
        } else {
            // No info available, guess based on ID
            SpriteRef(
                source = SpriteSource.VECTOR_FALLBACK,
                fallbackCategory = ItemSpriteProvider.guessCategoryFromId(itemId),
                fallbackMaterial = ItemSpriteProvider.MaterialType.IRON
            )
        }
    }

    /**
     * Check if an item has a dedicated sprite available
     */
    fun hasSprite(itemId: Int): Boolean {
        return localSpriteMappings.containsKey(itemId) || wikiSpriteNames.containsKey(itemId)
    }

    /**
     * Check if an item has a local sprite
     */
    fun hasLocalSprite(itemId: Int): Boolean {
        return localSpriteMappings.containsKey(itemId)
    }

    /**
     * Check if an item has a wiki sprite URL
     */
    fun hasWikiSprite(itemId: Int): Boolean {
        return wikiSpriteNames.containsKey(itemId)
    }

    /**
     * Get the wiki sprite URL for an item name
     * Uses OSRS Wiki's standard URL format
     */
    fun getWikiSpriteUrl(itemName: String): String {
        // Replace spaces with underscores and URL encode
        val encodedName = itemName.replace(" ", "_")
        return "https://oldschool.runescape.wiki/images/${encodedName}_detail.png"
    }

    /**
     * Get the wiki sprite URL for an item by ID
     */
    fun getWikiSpriteUrlForItem(itemId: Int): String? {
        return wikiSpriteNames[itemId]?.let { getWikiSpriteUrl(it) }
    }

    /**
     * Get local sprite ID for an item
     */
    fun getLocalSpriteId(itemId: Int): Int? {
        return localSpriteMappings[itemId]
    }

    /**
     * Get all items with wiki sprite mappings
     */
    fun getWikiMappedItems(): Set<Int> = wikiSpriteNames.keys

    /**
     * Get all items with local sprite mappings
     */
    fun getLocalMappedItems(): Set<Int> = localSpriteMappings.keys

    /**
     * Register a custom local sprite mapping
     * Useful for adding runtime-discovered sprites
     */
    private val customLocalMappings = mutableMapOf<Int, Int>()

    fun registerLocalSprite(itemId: Int, spriteId: Int) {
        customLocalMappings[itemId] = spriteId
    }

    /**
     * Register a custom wiki sprite mapping
     */
    private val customWikiMappings = mutableMapOf<Int, String>()

    fun registerWikiSprite(itemId: Int, wikiName: String) {
        customWikiMappings[itemId] = wikiName
    }

    // ========================================================================
    // Item Sprite Cache for External URL Sprites
    // ========================================================================

    /**
     * Cache for loaded item sprites (by item ID)
     * Prevents redundant network fetches for the same item
     */
    private val itemSpriteCache = mutableMapOf<Int, ImageBitmap>()
    private val failedItemLoads = mutableSetOf<Int>()
    private val loadingItems = mutableSetOf<Int>()
    private val cacheMutex = Mutex()

    /**
     * Get cached item sprite or null if not loaded
     */
    fun getCachedSprite(itemId: Int): ImageBitmap? = itemSpriteCache[itemId]

    /**
     * Check if item sprite is cached
     */
    fun isCached(itemId: Int): Boolean = itemSpriteCache.containsKey(itemId)

    /**
     * Check if item sprite failed to load
     */
    fun hasFailed(itemId: Int): Boolean = failedItemLoads.contains(itemId)

    /**
     * Check if item sprite is currently loading
     */
    fun isLoading(itemId: Int): Boolean = loadingItems.contains(itemId)

    /**
     * Mark item as loading
     */
    suspend fun markLoading(itemId: Int) {
        cacheMutex.withLock {
            loadingItems.add(itemId)
        }
    }

    /**
     * Cache a loaded item sprite
     */
    suspend fun cacheSprite(itemId: Int, bitmap: ImageBitmap) {
        cacheMutex.withLock {
            itemSpriteCache[itemId] = bitmap
            loadingItems.remove(itemId)
            failedItemLoads.remove(itemId)
        }
    }

    /**
     * Mark item as failed to load
     */
    suspend fun markFailed(itemId: Int) {
        cacheMutex.withLock {
            failedItemLoads.add(itemId)
            loadingItems.remove(itemId)
        }
    }

    /**
     * Clear the item sprite cache
     */
    suspend fun clearCache() {
        cacheMutex.withLock {
            itemSpriteCache.clear()
            failedItemLoads.clear()
            loadingItems.clear()
        }
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): ItemSpriteCacheStats {
        return ItemSpriteCacheStats(
            cachedCount = itemSpriteCache.size,
            failedCount = failedItemLoads.size,
            loadingCount = loadingItems.size
        )
    }

    data class ItemSpriteCacheStats(
        val cachedCount: Int,
        val failedCount: Int,
        val loadingCount: Int
    )

    // ========================================================================
    // Item Type Classification
    // ========================================================================

    /**
     * Item categories for sprite matching
     * Used when we need to find similar-looking sprites
     */
    enum class ItemType {
        WEAPON_MELEE,
        WEAPON_RANGED,
        WEAPON_MAGIC,
        ARMOR_HEAD,
        ARMOR_BODY,
        ARMOR_LEGS,
        ARMOR_HANDS,
        ARMOR_FEET,
        ARMOR_SHIELD,
        ARMOR_CAPE,
        JEWELRY_AMULET,
        JEWELRY_RING,
        TOOL,
        RESOURCE,
        FOOD,
        POTION,
        RUNE,
        AMMO,
        MISC
    }

    /**
     * Get the item type for categorization
     */
    fun getItemType(itemId: Int): ItemType {
        return when {
            // Weapons
            itemId in 1277..1333 -> ItemType.WEAPON_MELEE  // Swords
            itemId in 1351..1373 -> ItemType.WEAPON_MELEE  // Axes
            itemId in 1203..1215 -> ItemType.WEAPON_MELEE  // Daggers
            itemId in 841..861 -> ItemType.WEAPON_RANGED   // Bows

            // Armor
            itemId in 1115..1127 -> ItemType.ARMOR_BODY    // Platebodies
            itemId in 1067..1079 -> ItemType.ARMOR_LEGS    // Platelegs
            itemId in 1137..1163 -> ItemType.ARMOR_HEAD    // Helms
            itemId in 1171..1201 -> ItemType.ARMOR_SHIELD  // Shields

            // Resources
            itemId in 1511..1521 -> ItemType.RESOURCE      // Logs
            itemId in 436..453 -> ItemType.RESOURCE        // Ores
            itemId in 2349..2363 -> ItemType.RESOURCE      // Bars

            // Food
            itemId in 315..385 -> ItemType.FOOD            // Fish

            // Runes
            itemId in 554..566 -> ItemType.RUNE

            // Ammo
            itemId in 882..892 -> ItemType.AMMO

            // Default
            else -> ItemType.MISC
        }
    }
}
