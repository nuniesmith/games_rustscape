@file:OptIn(org.jetbrains.compose.resources.InternalResourceApi::class)

package com.rustscape.composeapp.generated.resources

import kotlin.OptIn
import kotlin.String
import kotlin.collections.MutableMap
import org.jetbrains.compose.resources.FontResource
import org.jetbrains.compose.resources.InternalResourceApi

private object CommonMainFont0 {
  public val press_start_2p: FontResource by 
      lazy { init_press_start_2p() }

  public val silkscreen: FontResource by 
      lazy { init_silkscreen() }

  public val silkscreen_bold: FontResource by 
      lazy { init_silkscreen_bold() }
}

@InternalResourceApi
internal fun _collectCommonMainFont0Resources(map: MutableMap<String, FontResource>) {
  map.put("press_start_2p", CommonMainFont0.press_start_2p)
  map.put("silkscreen", CommonMainFont0.silkscreen)
  map.put("silkscreen_bold", CommonMainFont0.silkscreen_bold)
}

internal val Res.font.press_start_2p: FontResource
  get() = CommonMainFont0.press_start_2p

private fun init_press_start_2p(): FontResource = org.jetbrains.compose.resources.FontResource(
  "font:press_start_2p",
    setOf(
      org.jetbrains.compose.resources.ResourceItem(setOf(),
    "composeResources/com.rustscape.composeapp.generated.resources/font/press_start_2p.ttf", -1, -1),
    )
)

internal val Res.font.silkscreen: FontResource
  get() = CommonMainFont0.silkscreen

private fun init_silkscreen(): FontResource = org.jetbrains.compose.resources.FontResource(
  "font:silkscreen",
    setOf(
      org.jetbrains.compose.resources.ResourceItem(setOf(),
    "composeResources/com.rustscape.composeapp.generated.resources/font/silkscreen.ttf", -1, -1),
    )
)

internal val Res.font.silkscreen_bold: FontResource
  get() = CommonMainFont0.silkscreen_bold

private fun init_silkscreen_bold(): FontResource = org.jetbrains.compose.resources.FontResource(
  "font:silkscreen_bold",
    setOf(
      org.jetbrains.compose.resources.ResourceItem(setOf(),
    "composeResources/com.rustscape.composeapp.generated.resources/font/silkscreen_bold.ttf", -1, -1),
    )
)
