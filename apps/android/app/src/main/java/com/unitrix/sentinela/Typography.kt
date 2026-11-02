package com.unitrix.sentinela

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

val SentinelaInterfaceFont = FontFamily(Font(R.font.manrope))
val SentinelaEditorialFont = FontFamily(Font(R.font.instrument_serif))
fun sentinelaTypography(): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = SentinelaInterfaceFont),
        displayMedium = base.displayMedium.copy(fontFamily = SentinelaInterfaceFont),
        displaySmall = base.displaySmall.copy(fontFamily = SentinelaInterfaceFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = SentinelaEditorialFont),
        headlineMedium = base.headlineMedium.copy(fontFamily = SentinelaEditorialFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = SentinelaEditorialFont),
        titleLarge = base.titleLarge.copy(fontFamily = SentinelaInterfaceFont),
        titleMedium = base.titleMedium.copy(fontFamily = SentinelaInterfaceFont),
        titleSmall = base.titleSmall.copy(fontFamily = SentinelaInterfaceFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = SentinelaInterfaceFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = SentinelaInterfaceFont),
        bodySmall = base.bodySmall.copy(fontFamily = SentinelaInterfaceFont),
        labelLarge = base.labelLarge.copy(fontFamily = SentinelaInterfaceFont),
        labelMedium = base.labelMedium.copy(fontFamily = SentinelaInterfaceFont),
        labelSmall = base.labelSmall.copy(fontFamily = SentinelaInterfaceFont)
    )
}
