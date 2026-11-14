package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
  primary = EmeraldPrimary,
  onPrimary = Color.White,
  primaryContainer = EmeraldContainer,
  onPrimaryContainer = OnEmeraldContainer,
  secondary = SaffronGold,
  onSecondary = Color.White,
  secondaryContainer = SaffronGoldLight,
  onSecondaryContainer = OnSaffronContainer,
  tertiary = EmeraldDeep,
  onTertiary = Color.White,
  background = WarmIvory,
  onBackground = EspressoInk,
  surface = WarmCreamSurface,
  onSurface = EspressoInk,
  surfaceVariant = WarmSandVariant,
  onSurfaceVariant = WarmSlate,
  outline = WarmBorder,
  error = CrimsonError,
  onError = Color.White,
  errorContainer = CrimsonErrorContainer,
  onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
  primary = EmeraldMint,
  onPrimary = Color(0xFF00382D),
  primaryContainer = EmeraldDeep,
  onPrimaryContainer = EmeraldContainer,
  secondary = SaffronBright,
  onSecondary = Color(0xFF3E2703),
  secondaryContainer = Color(0xFF56390A),
  onSecondaryContainer = SaffronGoldLight,
  tertiary = EmeraldMint,
  onTertiary = Color(0xFF00382D),
  background = DarkBg,
  onBackground = WarmPaperText,
  surface = DarkSurface,
  onSurface = WarmPaperText,
  surfaceVariant = DarkSurfaceVariant,
  onSurfaceVariant = Color(0xFFC5BEB5),
  outline = Color(0xFF3B4540),
  error = Color(0xFFFFB4AB),
  onError = Color(0xFF690005),
  errorContainer = Color(0xFF93000A),
  onErrorContainer = Color(0xFFFFDAD6)
)

val TurkishqShapes = Shapes(
  extraSmall = RoundedCornerShape(6.dp),
  small = RoundedCornerShape(10.dp),
  medium = RoundedCornerShape(14.dp),
  large = RoundedCornerShape(18.dp),
  extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun TurkishqRetailTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  MaterialTheme(
    colorScheme = colorScheme,
    typography = TurkishqTypography,
    shapes = TurkishqShapes,
    content = content
  )
}
