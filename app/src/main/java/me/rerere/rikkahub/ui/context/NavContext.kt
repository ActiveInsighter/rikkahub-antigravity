package me.rerere.rikkahub.ui.context

import androidx.compose.runtime.compositionLocalOf
import androidx.navigation3.runtime.NavKey
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.MainTab

class Navigator(
    private val getActiveBackStack: () -> MutableList<NavKey>,
    private val getTabBackStack: (MainTab) -> MutableList<NavKey>,
    private val onSwitchTab: (MainTab) -> Unit,
) {
    fun navigate(screen: Screen, builder: NavigateOptionsBuilder.() -> Unit = {}) {
        navigateOnStack(getActiveBackStack(), screen, builder)
    }

    fun navigateInTab(
        tab: MainTab,
        screen: Screen,
        replaceRoot: Boolean = false,
        builder: NavigateOptionsBuilder.() -> Unit = {},
    ) {
        onSwitchTab(tab)
        val stack = getTabBackStack(tab)
        if (replaceRoot) {
            stack.clear()
            stack.add(screen)
        } else {
            navigateOnStack(stack, screen, builder)
        }
    }

    fun switchTab(tab: MainTab, popToRoot: Boolean = false) {
        onSwitchTab(tab)
        if (popToRoot) {
            val stack = getTabBackStack(tab)
            while (stack.size > 1) {
                stack.removeLastOrNull()
            }
        }
    }

    private fun navigateOnStack(
        backStack: MutableList<NavKey>,
        screen: Screen,
        builder: NavigateOptionsBuilder.() -> Unit,
    ) {
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
