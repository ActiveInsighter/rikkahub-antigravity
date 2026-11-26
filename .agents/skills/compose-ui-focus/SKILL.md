---
name: compose-ui-focus
description: 处理 Jetpack Compose 的焦点与软键盘行为。凡是涉及抽屉/侧边栏、弹窗、底部弹层、TextField 或「打开某个界面时输入框自动获得焦点、软键盘弹起」的问题，或者新增/修改任何包含输入框的覆盖层时，使用本 skill。
---

# Compose 焦点与软键盘规则

## 硬性规则

**任何界面都不允许自动把焦点交给输入框，从而弹起软键盘。**

- 打开抽屉、侧边栏、弹窗、底部弹层、页面时，输入框不得自动获得焦点。
- 只有用户主动点击输入框（或点击明确的「搜索/输入」入口）时，才允许 `requestFocus()` 并弹键盘。
- 用户按键盘搜索/完成、点击清除、切换页面、关闭覆盖层后，必须 `clearFocus(force = true)` 并 `hide()` 软键盘。
- 搜索类入口优先模仿 Chat / Agent 抽屉：抽屉内不放常驻输入框，放一个不可编辑的入口，点击后再进入编辑态。

## 为什么必须这样做

Material3 的 `ModalNavigationDrawer` 在打开时会主动把焦点交给抽屉内容：

```kotlin
LaunchedEffect(drawerState.isOpen) {
    if (drawerState.isOpen) focusRequester.requestFocus()
}
```

触摸模式下 `Modifier.clickable` 的 `Focusability` 是 `SystemDefined`，不会被普通焦点搜索命中；
如果抽屉里放了一个常驻的 `BasicTextField`（`Focusability.Always`），焦点就会直接落到它上面，
软键盘随之弹起。`OutlinedTextField` / `TextField` 同理。

## 正确写法

1. 抽屉里只渲染**不可编辑**的表面（`Surface` + `Icon` + `Text`），点击后才把真正的输入框放进组合：

```kotlin
var editing by remember { mutableStateOf(false) }
val focusRequester = remember { FocusRequester() }

LaunchedEffect(editing) { if (editing) focusRequester.requestFocus() }

// 关闭覆盖层时退出编辑态，避免下次打开时输入框仍在树里被自动聚焦
LaunchedEffect(isDrawerOpen) {
    if (!isDrawerOpen) editing = false
}

Surface(modifier = Modifier.clickable { editing = true }) {
    if (editing) {
        BasicTextField(/* ... */ modifier = Modifier.focusRequester(focusRequester))
    } else {
        Text(query.ifEmpty { "搜索" })
    }
}
```

2. 打开覆盖层时主动清焦点并收起键盘：

```kotlin
LaunchedEffect(drawerState.isOpen) {
    if (drawerState.isOpen) {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }
}
```

3. 页面级输入框（如聊天输入框）在发送、跳转、打开覆盖层后同样要清焦点。

## 自查清单

- [ ] 抽屉/弹层打开时，树里是否存在可直接聚焦的 `BasicTextField` / `TextField`？有就改成按需组合。
- [ ] 覆盖层关闭后，输入框是否退出编辑态并清焦点？
- [ ] 是否有 `LaunchedEffect` 在没有用户操作时调用 `requestFocus()`？（搜索页这类用户主动进入的页面除外）
- [ ] 修改后手动验证：打开侧边栏、切换页面、关闭弹层都不会弹起软键盘。

例外：用户主动进入的搜索页（如 `SearchPage`）、画板文字工具（`SketchTextInput`）可以在进入时聚焦，这属于明确的用户意图。
