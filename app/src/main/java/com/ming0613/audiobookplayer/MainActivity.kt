package com.ming0613.audiobookplayer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ming0613.audiobookplayer.ui.library.LibraryScreen
import com.ming0613.audiobookplayer.ui.player.PlayerScreen
import com.ming0613.audiobookplayer.ui.theme.AudiobookPlayerTheme

/**
 * App 唯一入口 Activity。
 * 单 Activity 架构：所有"页面"都是 Compose 函数，由 NavHost 负责切换。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudiobookPlayerTheme {
                AppNavHost()
            }
        }
    }
}

private object Routes {
    const val LIBRARY = "library"
    const val PLAYER = "player/{bookId}"

    fun player(bookId: String) = "player/${Uri.encode(bookId)}"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onBookClick = { bookId -> navController.navigate(Routes.player(bookId)) }
            )
        }
        composable(
            route = Routes.PLAYER,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType })
        ) { backStackEntry ->
            // 注意：Navigation 库解析路由时已自动解码一次，这里直接用原值，切勿再 Uri.decode
            val bookId = backStackEntry.arguments?.getString("bookId").orEmpty()
            PlayerScreen(
                bookId = bookId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
