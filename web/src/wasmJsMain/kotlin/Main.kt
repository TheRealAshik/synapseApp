import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeViewport
import com.synapse.social.studioasinc.shared.di.commentModule
import com.synapse.social.studioasinc.shared.di.storageModule
import com.synapse.social.studioasinc.shared.theme.SynapseColorScheme
import com.synapse.social.studioasinc.shared.theme.SynapseTheme as SharedTheme
import com.synapse.social.studioasinc.web.di.webModule
import com.synapse.social.studioasinc.web.ui.WebAuthScreen
import com.synapse.social.studioasinc.web.ui.WebFeedScreen
import kotlinx.browser.document
import org.koin.core.context.startKoin

private fun SynapseColorScheme.toComposeColorScheme(): ColorScheme {
    return ColorScheme(
        primary = Color(this.primary),
        onPrimary = Color(this.onPrimary),
        primaryContainer = Color(this.primaryContainer),
        onPrimaryContainer = Color(this.onPrimaryContainer),
        secondary = Color(this.secondary),
        onSecondary = Color(this.onSecondary),
        secondaryContainer = Color(this.secondaryContainer),
        onSecondaryContainer = Color(this.onSecondaryContainer),
        tertiary = Color(this.tertiary),
        onTertiary = Color(this.onTertiary),
        tertiaryContainer = Color(this.tertiaryContainer),
        onTertiaryContainer = Color(this.onTertiaryContainer),
        error = Color(this.error),
        onError = Color(this.onError),
        errorContainer = Color(this.errorContainer),
        onErrorContainer = Color(this.onErrorContainer),
        background = Color(this.background),
        onBackground = Color(this.onBackground),
        surface = Color(this.surface),
        onSurface = Color(this.onSurface),
        surfaceVariant = Color(this.surfaceVariant),
        onSurfaceVariant = Color(this.onSurfaceVariant),
        outline = Color(this.outline),
        outlineVariant = Color(this.outlineVariant),
        scrim = Color(this.scrim),
        inverseSurface = Color(this.inverseSurface),
        inverseOnSurface = Color(this.inverseOnSurface),
        inversePrimary = Color(this.inversePrimary),
        surfaceTint = Color(this.surfaceTint),
        surfaceContainer = Color(this.surfaceContainer),
        surfaceContainerLow = Color(this.surfaceContainerLow),
        surfaceContainerHigh = Color(this.surfaceContainerHigh),
        surfaceContainerHighest = Color(this.surfaceContainerHighest),
        surfaceContainerLowest = Color(this.surfaceContainerLowest),
        surfaceDim = Color(this.surfaceContainerLow),
        surfaceBright = Color(this.surfaceContainerHigh)
    )
}

enum class WebScreen {
    Landing, Login, Feed
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        modules(storageModule, commentModule, webModule)
    }

    ComposeViewport(document.body!!) {
        val darkTheme = isSystemInDarkTheme()
        val colorScheme = if (darkTheme) SharedTheme.colors.Dark.toComposeColorScheme() else SharedTheme.colors.Light.toComposeColorScheme()

        MaterialTheme(colorScheme = colorScheme) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                var currentScreen by remember { mutableStateOf(WebScreen.Landing) }

                when (currentScreen) {
                    WebScreen.Landing -> WebPremiumLandingPage(onGetStarted = { currentScreen = WebScreen.Login })
                    WebScreen.Login -> WebAuthScreen(onLoginSuccess = { currentScreen = WebScreen.Feed })
                    WebScreen.Feed -> WebFeedScreen()
                }
            }
        }
    }
}

@Composable
fun WebPremiumLandingPage(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Synapse Web",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Experience premium social networking right in your browser.",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Card(
            modifier = Modifier.padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "Status: Online", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onGetStarted) {
                    Text("Get Started")
                }
            }
        }
    }
}
