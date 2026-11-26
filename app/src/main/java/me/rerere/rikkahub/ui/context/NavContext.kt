package me.rerere.rikkahub.ui.context

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavKey
import me.rerere.rikkahub.ui.components.nav.MainTab

class Navigator(
    private val getActiveBackStack: () -> MutableList<NavKey>,
    private val onSwitchTab: (MainTab) -> Unit = {},
    private val getTabBackStack: (MainTab) -> MutableList<NavKey> = { getActiveBackStack() },
) {
    constructor(backStack: MutableList<NavKey>) : this(
        getActiveBackStack = { backStack },
    )

    fun navigate(screen: Screen, builder: NavigateOptionsBuilder.() -> Unit = {}) {
        val backStack = getActiveBackStack()
        val options = NavigateOptionsBuilder().apply(builder)

        options.popUpToScreen?.let { target ->
            val targetIndex = backStack.indexOfLast { it == target }
            if (targetIndex != -1) {
                val removeFromIndex = if (options.popUpToInclusive) targetIndex else targetIndex + 1
                repeat(backStack.size - removeFromIndex) {
                    backStack.removeLastOrNull()
                }
            }
        }

        if (options.launchSingleTop && backStack.lastOrNull() == screen) {
            return
        }

        backStack.add(screen)
    }

    fun clearAndNavigate(screen: Screen) {
        val backStack = getActiveBackStack()
        backStack.clear()
        backStack.add(screen)
    }

    fun switchTab(tab: MainTab) {
        onSwitchTab(tab)
    }

    fun navigateInTab(tab: MainTab, screen: Screen, replaceRoot: Boolean = false) {
        val backStack = getTabBackStack(tab)
        if (replaceRoot) {
            backStack.clear()
            backStack.add(screen)
        } else if (backStack.lastOrNull() != screen) {
            backStack.add(screen)
        }
        onSwitchTab(tab)
    }

    fun contains(screen: Screen): Boolean = getActiveBackStack().any { it == screen }

    fun popBackStack() {
        val backStack = getActiveBackStack()
        if (backStack.size > 1) backStack.removeLastOrNull()
    }
}

class NavigateOptionsBuilder {
    internal var popUpToScreen: Screen? = null
    internal var popUpToInclusive: Boolean = false
    var launchSingleTop: Boolean = false

    fun popUpTo(screen: Screen, builder: PopUpToBuilder.() -> Unit = {}) {
        val options = PopUpToBuilder().apply(builder)
        popUpToScreen = screen
        popUpToInclusive = options.inclusive
    }
}

class PopUpToBuilder {
    var inclusive: Boolean = false
}

val LocalNavController = compositionLocalOf<Navigator> {
    error("No Navigator provided")
}
