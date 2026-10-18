package drs.chat.app.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import drs.chat.app.chatlist.ChatListScreen
import drs.chat.app.conversation.ConversationScreen
import drs.chat.app.onboarding.OnboardingScreen
import drs.chat.app.settings.SettingsScreen

object Routes {
    const val ONBOARDING = "onboarding"
    const val CHAT_LIST = "chatList"
    const val CONVERSATION = "conversation/{contactHex}"
    const val SETTINGS = "settings"

    fun conversation(contactHex: String) = "conversation/$contactHex"
}

@Composable
fun DrsChatNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.ONBOARDING) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onContinue = { nav.navigate(Routes.CHAT_LIST) { popUpTo(Routes.ONBOARDING) { inclusive = true } } })
        }
        composable(Routes.CHAT_LIST) {
            ChatListScreen(
                onContactClick = { hex -> nav.navigate(Routes.conversation(hex)) },
                onSettingsClick = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(
            route = Routes.CONVERSATION,
            arguments = listOf(navArgument("contactHex") { type = NavType.StringType }),
        ) { backStack ->
            val hex = backStack.arguments?.getString("contactHex") ?: ""
            ConversationScreen(contactHex = hex, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
