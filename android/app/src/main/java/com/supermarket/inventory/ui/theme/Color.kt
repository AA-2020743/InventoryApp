package com.supermarket.inventory.ui.theme

import androidx.compose.ui.graphics.Color

// A full Material 3 role set in the app's green identity. Previously only a
// handful of roles were defined and everything else fell back to Material's
// baseline palette, which is purple - so cards, switches, chips and
// segmented buttons rendered lilac-grey against green branding. Every role
// the components actually reach for is spelled out here instead.

// ---- Light ----
val GreenPrimaryLight = Color(0xFF1E5631)
val OnPrimaryLight = Color(0xFFFFFFFF)
val GreenPrimaryContainerLight = Color(0xFFB6F2C9)
val OnPrimaryContainerLight = Color(0xFF002110)

val GreenSecondaryLight = Color(0xFF4E6355)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFD0E8D6)
val OnSecondaryContainerLight = Color(0xFF0B1F14)

// A muted teal, used by the components that want a third accent (e.g. the
// worker-debt figure on the dashboard) - close enough to the green to stay
// in family, distinct enough to read as "different kind of number".
val TertiaryLight = Color(0xFF3A6470)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFBEEAF8)
val OnTertiaryContainerLight = Color(0xFF001F27)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

val BackgroundLight = Color(0xFFFBFDF8)
val OnBackgroundLight = Color(0xFF191C19)
val SurfaceLight = Color(0xFFFBFDF8)
val OnSurfaceLight = Color(0xFF191C19)
val SurfaceVariantLight = Color(0xFFDCE5DC)
val OnSurfaceVariantLight = Color(0xFF414942)
val OutlineLight = Color(0xFF717972)
val OutlineVariantLight = Color(0xFFC0C9C0)
val InverseSurfaceLight = Color(0xFF2E312E)
val InverseOnSurfaceLight = Color(0xFFF0F1EC)
val InversePrimaryLight = Color(0xFF9CD5AE)

// Card/sheet/menu backgrounds. Kept as a gentle tonal ladder off the
// background so stacked surfaces stay distinguishable without borders.
val SurfaceDimLight = Color(0xFFDADBD6)
val SurfaceBrightLight = Color(0xFFFBFDF8)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF5F7F1)
val SurfaceContainerLight = Color(0xFFEFF1EC)
val SurfaceContainerHighLight = Color(0xFFE9EBE6)
val SurfaceContainerHighestLight = Color(0xFFE3E6E0)

// ---- Dark ----
val GreenPrimaryDark = Color(0xFF9CD5AE)
val OnPrimaryDark = Color(0xFF00391D)
val GreenPrimaryContainerDark = Color(0xFF0A5027)
val OnPrimaryContainerDark = Color(0xFFB8F2C9)

val GreenSecondaryDark = Color(0xFFB4CCBB)
val OnSecondaryDark = Color(0xFF1F3528)
val SecondaryContainerDark = Color(0xFF354B3D)
val OnSecondaryContainerDark = Color(0xFFD0E8D6)

val TertiaryDark = Color(0xFFA3CDDC)
val OnTertiaryDark = Color(0xFF033541)
val TertiaryContainerDark = Color(0xFF214C58)
val OnTertiaryContainerDark = Color(0xFFBEEAF8)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF10140F)
val OnBackgroundDark = Color(0xFFE1E3DE)
val SurfaceDark = Color(0xFF10140F)
val OnSurfaceDark = Color(0xFFE1E3DE)
val SurfaceVariantDark = Color(0xFF2C332D)
val OnSurfaceVariantDark = Color(0xFFC0C9C0)
val OutlineDark = Color(0xFF8A938B)
val OutlineVariantDark = Color(0xFF414942)
val InverseSurfaceDark = Color(0xFFE1E3DE)
val InverseOnSurfaceDark = Color(0xFF2E312E)
val InversePrimaryDark = Color(0xFF1E5631)

val SurfaceDimDark = Color(0xFF10140F)
val SurfaceBrightDark = Color(0xFF353A34)
val SurfaceContainerLowestDark = Color(0xFF0B0F0A)
val SurfaceContainerLowDark = Color(0xFF181C17)
val SurfaceContainerDark = Color(0xFF1C201B)
val SurfaceContainerHighDark = Color(0xFF262B25)
val SurfaceContainerHighestDark = Color(0xFF313630)

// Semantic money colors, used directly rather than through a scheme role:
// profit/loss/warning mean the same thing in either theme, and each has a
// light- and dark-mode variant so it stays legible on both grounds.
val ProfitGreen = Color(0xFF2E7D32)
val ProfitGreenDark = Color(0xFF7BD98A)
val LossRed = Color(0xFFC62828)
val LossRedDark = Color(0xFFFF8A80)
val WarningAmber = Color(0xFFB26A00)
val WarningAmberDark = Color(0xFFFFC46B)
