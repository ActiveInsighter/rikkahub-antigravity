@file:Suppress("unused")

package me.rerere.ui.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * DataTable（自定义布局 + 横向滚动 + 行内等高）
 * - 使用标准 Layout + Intrinsic 测量，完全支持 LookaheadScope 与 FlowRow 等固有尺寸查询
 * - 高度自适应内容（不提供纵向滚动）
 * - 宽度可超出视口，外层内置 horizontalScroll
 */
@Composable
fun DataTable(
    headers: List<@Composable () -> Unit>,
    rows: List<List<@Composable () -> Unit>>,
    modifier: Modifier = Modifier,
    cellPadding: Dp = 4.dp,
    cellHorizontalPadding: Dp = cellPadding,
    cellVerticalPadding: Dp = cellPadding,
    cellBorder: BorderStroke? = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    rowDividerColor: Color? = null,
    headerCornerRadius: Dp = 0.dp,
    headerBackground: Color = MaterialTheme.colorScheme.surfaceVariant,
    zebraStriping: Boolean = false,
    columnMinWidths: List<Dp> = emptyList(),
    columnMaxWidths: List<Dp> = emptyList(),
    cellAlignment: Alignment = Alignment.CenterStart,
    // 按列覆盖 cellAlignment，缺省的列沿用 cellAlignment
    columnAlignments: List<Alignment> = emptyList(),
    // 按单元格覆盖所在列的对齐方式，row 是 rows 的下标；返回 null 时沿用列的对齐方式
    bodyCellAlignment: ((row: Int, column: Int) -> Alignment?)? = null,
    outerBorder: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shape: Shape = MaterialTheme.shapes.small,
    stretchToFillWidth: Boolean = true,
) {
    val hScroll = rememberScrollState()
    val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
    var viewportMaxWidth by remember { mutableIntStateOf(0) }

    val columnCount = max(headers.size, rows.maxOfOrNull { it.size } ?: 0)
    val rowCount = rows.size

    fun headerShapeFor(c: Int): Shape {
        if (headerCornerRadius <= 0.dp || columnCount == 0) return RectangleShape
        val startRadius = if (c == 0) headerCornerRadius else 0.dp
        val endRadius = if (c == columnCount - 1) headerCornerRadius else 0.dp
        return RoundedCornerShape(
            topStart = startRadius,
            bottomStart = startRadius,
            topEnd = endRadius,
            bottomEnd = endRadius,
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .then(if (outerBorder != null) Modifier.border(outerBorder, shape) else Modifier)
            .onSizeChanged { size ->
                if (size.width > 0 && viewportMaxWidth != size.width) {
                    viewportMaxWidth = size.width
                }
            }
            .horizontalScroll(hScroll),
    ) {
        if (columnCount > 0) {
            val measurePolicy = remember(
                columnCount,
                rowCount,
                columnMinWidths,
                columnMaxWidths,
                stretchToFillWidth,
                viewportMaxWidth,
            ) {
                object : MeasurePolicy {
                    private fun IntrinsicMeasureScope.computeColWidths(
                        measurables: List<IntrinsicMeasurable>,
                    ): IntArray {
                        val minWidthsPx = IntArray(columnCount) { i -> columnMinWidths.getOrNull(i)?.roundToPx() ?: 0 }
                        val maxWidthsPx = IntArray(columnCount) { i -> columnMaxWidths.getOrNull(i)?.roundToPx() ?: Int.MAX_VALUE }
                        val colWidths = IntArray(columnCount) { 0 }
                        for (c in 0 until columnCount) {
                            val intrinsicW = measurables.getOrNull(c)?.maxIntrinsicWidth(Constraints.Infinity) ?: 0
                            colWidths[c] = max(colWidths[c], max(intrinsicW, minWidthsPx[c])).coerceAtMost(maxWidthsPx[c])
                        }
                        for (r in 0 until rowCount) {
                            for (c in 0 until columnCount) {
                                val idx = columnCount + r * columnCount + c
                                val intrinsicW = measurables.getOrNull(idx)?.maxIntrinsicWidth(Constraints.Infinity) ?: 0
                                colWidths[c] = max(colWidths[c], max(intrinsicW, minWidthsPx[c])).coerceAtMost(maxWidthsPx[c])
                            }
                        }
                        val naturalWidth = colWidths.sum()
                        if (stretchToFillWidth && viewportMaxWidth > naturalWidth && naturalWidth > 0) {
                            val extra = viewportMaxWidth - naturalWidth
                            var distributed = 0
                            for (c in 0 until columnCount) {
                                val add = if (c == columnCount - 1) {
                                    extra - distributed
                                } else {
                                    (extra.toLong() * colWidths[c] / naturalWidth).toInt()
                                }
                                colWidths[c] += add
                                distributed += add
                            }
                        }
                        return colWidths
                    }

                    override fun MeasureScope.measure(
                        measurables: List<Measurable>,
                        constraints: Constraints,
                    ): MeasureResult {
                        val colWidths = computeColWidths(measurables)
                        var headerHeight = 0
                        for (c in 0 until columnCount) {
                            val h = measurables.getOrNull(c)?.minIntrinsicHeight(colWidths[c]) ?: 0
                            headerHeight = max(headerHeight, h)
                        }
                        val rowHeights = IntArray(rowCount) { r ->
                            var h = 0
                            for (c in 0 until columnCount) {
                                val idx = columnCount + r * columnCount + c
                                val cellH = measurables.getOrNull(idx)?.minIntrinsicHeight(colWidths[c]) ?: 0
                                h = max(h, cellH)
                            }
                            h
                        }

                        fun constraintsFor(colWidth: Int, minH: Int): Constraints {
                            val safeColWidth = colWidth.coerceAtLeast(0)
                            val safeMinH = minH.coerceAtLeast(0)
                            return Constraints(
                                minWidth = safeColWidth,
                                maxWidth = safeColWidth,
                                minHeight = safeMinH,
                                maxHeight = Constraints.Infinity,
                            )
                        }

                        val headerPlaceables = Array(columnCount) { c ->
                            measurables[c].measure(constraintsFor(colWidths[c], headerHeight))
                        }
                        val actualHeaderHeight = headerPlaceables.maxOfOrNull { it.height } ?: headerHeight

                        val bodyPlaceables = Array(rowCount * columnCount) { i ->
                            val r = i / columnCount
                            val c = i % columnCount
                            measurables[columnCount + i].measure(constraintsFor(colWidths[c], rowHeights[r]))
                        }
                        val actualRowHeights = IntArray(rowCount) { r ->
                            var h = rowHeights[r]
                            for (c in 0 until columnCount) {
                                h = max(h, bodyPlaceables[r * columnCount + c].height)
                            }
                            h
                        }

                        val tableWidth = colWidths.sum()
                        val tableHeight = actualHeaderHeight + actualRowHeights.sum()
                        val finalWidth = tableWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
                        val finalHeight = tableHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

                        return layout(finalWidth, finalHeight) {
                            var x = 0
                            for (c in 0 until columnCount) {
                                headerPlaceables[c].placeRelative(x, 0)
                                x += colWidths[c]
                            }
                            var y = actualHeaderHeight
                            for (r in 0 until rowCount) {
                                x = 0
                                for (c in 0 until columnCount) {
                                    bodyPlaceables[r * columnCount + c].placeRelative(x, y)
                                    x += colWidths[c]
                                }
                                y += actualRowHeights[r]
                            }
                        }
                    }

                    override fun IntrinsicMeasureScope.minIntrinsicWidth(
                        measurables: List<IntrinsicMeasurable>,
                        height: Int,
                    ): Int = computeColWidths(measurables).sum()

                    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                        measurables: List<IntrinsicMeasurable>,
                        height: Int,
                    ): Int = computeColWidths(measurables).sum()

                    override fun IntrinsicMeasureScope.minIntrinsicHeight(
                        measurables: List<IntrinsicMeasurable>,
                        width: Int,
                    ): Int {
                        val colWidths = computeColWidths(measurables)
                        var total = 0
                        var headerH = 0
                        for (c in 0 until columnCount) {
                            headerH = max(headerH, measurables.getOrNull(c)?.minIntrinsicHeight(colWidths[c]) ?: 0)
                        }
                        total += headerH
                        for (r in 0 until rowCount) {
                            var rowH = 0
                            for (c in 0 until columnCount) {
                                val idx = columnCount + r * columnCount + c
                                rowH = max(rowH, measurables.getOrNull(idx)?.minIntrinsicHeight(colWidths[c]) ?: 0)
                            }
                            total += rowH
                        }
                        return total
                    }

                    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                        measurables: List<IntrinsicMeasurable>,
                        width: Int,
                    ): Int = minIntrinsicHeight(measurables, width)
                }
            }

            Layout(
                content = {
                    for (c in 0 until columnCount) {
                        CellBox(
                            horizontalPadding = cellHorizontalPadding,
                            verticalPadding = cellVerticalPadding,
                            border = cellBorder,
                            bottomDividerColor = null,
                            backgroundShape = headerShapeFor(c),
                            background = headerBackground,
                            alignment = columnAlignments.getOrNull(c) ?: cellAlignment,
                        ) {
                            headers.getOrNull(c)?.invoke()
                        }
                    }
                    for (r in 0 until rowCount) {
                        val bg = if (zebraStriping && r % 2 == 1) surfaceContainer else Color.Transparent
                        val divider = if (r < rowCount - 1) rowDividerColor else null
                        for (c in 0 until columnCount) {
                            CellBox(
                                horizontalPadding = cellHorizontalPadding,
                                verticalPadding = cellVerticalPadding,
                                border = cellBorder,
                                bottomDividerColor = divider,
                                backgroundShape = RectangleShape,
                                background = bg,
                                alignment = bodyCellAlignment?.invoke(r, c)
                                    ?: columnAlignments.getOrNull(c)
                                    ?: cellAlignment,
                            ) {
                                rows[r].getOrNull(c)?.invoke()
                            }
                        }
                    }
                },
                measurePolicy = measurePolicy,
            )
        }
    }
}

@Composable
private fun CellBox(
    horizontalPadding: Dp,
    verticalPadding: Dp,
    border: BorderStroke?,
    bottomDividerColor: Color?,
    backgroundShape: Shape,
    background: Color,
    alignment: Alignment,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .then(if (background != Color.Transparent) Modifier.background(background, backgroundShape) else Modifier)
            .then(if (border != null) Modifier.border(border) else Modifier)
            .then(
                if (bottomDividerColor != null) {
                    Modifier.drawBehind {
                        val stroke = 0.8.dp.toPx()
                        drawLine(
                            color = bottomDividerColor,
                            start = Offset(0f, size.height - stroke / 2f),
                            end = Offset(size.width, size.height - stroke / 2f),
                            strokeWidth = stroke,
                        )
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        contentAlignment = alignment,
    ) {
        content()
    }
}

// -------------------- 示例 --------------------
@Preview(showBackground = true)
@Composable
private fun DataTablePreview() {
    Surface {
        val headers = listOf<@Composable () -> Unit>(
            { Text("Semester", style = MaterialTheme.typography.labelLarge) },
            { Text("Attendance", style = MaterialTheme.typography.labelLarge) },
            { Text("Notes / Example", style = MaterialTheme.typography.labelLarge) },
        )

        val rows = listOf<List<@Composable () -> Unit>>(
            listOf<@Composable () -> Unit>(
                { Text("Fall 2024") },
                { Text("Excellent", style = MaterialTheme.typography.bodyMedium) },
                { Text("x² + y² = 1") },
            ),
            listOf(
                { Text("Fall 2024") },
                { Text("Good", style = MaterialTheme.typography.bodyMedium) },
                { Text("∑ k = n(n+1)/2", maxLines = 2, overflow = TextOverflow.Ellipsis) },
            ),
            listOf(
                { Text("Fall 2024") },
                { Text("Fair", style = MaterialTheme.typography.bodyMedium) },
                { Text("这行更高会把整行拉齐! 这是一个很长的文本用来测试换行功能!\nhaha") },
            ),
        )

        DataTable(
            headers = headers,
            rows = rows,
            columnMinWidths = listOf(60.dp, 100.dp, 80.dp),
            columnMaxWidths = listOf(120.dp, 100.dp, 200.dp),
            zebraStriping = false,
        )
    }
}
