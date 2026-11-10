package com.adampay.app.core.locale

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.LayoutDirection
import com.adampay.app.domain.model.AppLanguage
import java.util.Locale

/**
 * Language modes map to two locales:
 *  - UI locale: what the interface is written in (and layout direction).
 *  - Term locale: what category and pocket names use.
 *
 *  MIXED   -> UI Arabic,  category / pocket names English (default)
 *  ARABIC  -> UI Arabic,  names Arabic
 *  ENGLISH -> UI English, names English
 *
 * Arabic uses Latin digits (ar-JO with the nu-latn extension), so amounts read "12.500" everywhere.
 */
object AppLocales {
    val Arabic: Locale = Locale.forLanguageTag("ar-JO-u-nu-latn")
    val English: Locale = Locale.forLanguageTag("en-US")

    fun ui(language: AppLanguage): Locale = if (language == AppLanguage.ENGLISH) English else Arabic
    fun terms(language: AppLanguage): Locale = if (language == AppLanguage.ARABIC) Arabic else English
    fun isRtl(language: AppLanguage): Boolean = language != AppLanguage.ENGLISH
}

/** Context that answers resource lookups in a given locale but otherwise behaves like [base] (activity lookups still work). */
class LocalizedContext(base: Context, private val res: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = res
    override fun getAssets(): AssetManager = res.assets
}

fun Context.withLocale(locale: Locale): Context {
    val config = Configuration(resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    val res = createConfigurationContext(config).resources
    return LocalizedContext(this, res)
}

/** Resolves strings for non-Compose code (notifications, services, workers). */
class Localizer(private val context: Context, val language: AppLanguage) {
    val ui: Context by lazy { context.withLocale(AppLocales.ui(language)) }
    val terms: Context by lazy { context.withLocale(AppLocales.terms(language)) }
    fun string(@StringRes id: Int, vararg args: Any): String = ui.getString(id, *args)
    fun term(@StringRes id: Int, vararg args: Any): String = terms.getString(id, *args)
}

val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.MIXED }
val LocalTermResources = staticCompositionLocalOf<Resources?> { null }

/** Provides UI-locale resources, RTL/LTR and term resources for the whole tree. Switching is instant (no activity restart). */
@Composable
fun ProvideAppLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val baseConfig = LocalConfiguration.current
    val uiContext = remember(base, baseConfig, language) { base.withLocale(AppLocales.ui(language)) }
    val termContext = remember(base, baseConfig, language) { base.withLocale(AppLocales.terms(language)) }
    CompositionLocalProvider(
        LocalContext provides uiContext,
        LocalResources provides uiContext.resources,
        LocalConfiguration provides uiContext.resources.configuration,
        LocalLayoutDirection provides if (AppLocales.isRtl(language)) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalTermResources provides termContext.resources,
        LocalAppLanguage provides language,
        content = content,
    )
}

/** A term in the term locale (English in the default mixed mode). */
@Composable
@ReadOnlyComposable
fun termResource(@StringRes id: Int, vararg args: Any): String {
    val res = LocalTermResources.current ?: LocalResources.current
    return if (args.isEmpty()) res.getString(id) else res.getString(id, *args)
}

/** [Bidi.join] in the current layout direction. */
@Composable
@ReadOnlyComposable
fun bidiJoin(parts: List<String>, separator: String = " · "): String =
    Bidi.join(parts, separator, LocalLayoutDirection.current == LayoutDirection.Rtl)
