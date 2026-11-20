@file:Suppress("unused")

package me.rerere.ui.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * DataTable（自定义布局 + 横向滚动 + 行内等高）
 * - 使用标准 Layout 支持 LookaheadScope 中的固有尺寸查询
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

    if (columnCount == 0) return

    val measurePolicy = remember(columnCount, columnMinWidths, columnMaxWidths, stretchToFillWidth) {
        object : MeasurePolicy {
            private fun IntrinsicMeasureScope.columnWidths(
                cells: List<IntrinsicMeasurable>,
                targetWidth: Int = 0,
                intrinsicWidth: (IntrinsicMeasurable) -> Int,
            ): IntArray {
                val widths = IntArray(columnCount) { columnMinWidths.getOrNull(it)?.roundToPx() ?: 0 }
                cells.forEachIndexed { index, cell ->
                    val column = index % columnCount
                    widths[column] = max(widths[column], intrinsicWidth(cell))
                }
                widths.forEachIndexed { column, width ->
                    widths[column] = width.coerceAtMost(
                        columnMaxWidths.getOrNull(column)?.roundToPx() ?: Int.MAX_VALUE
                    )
                }
                val naturalWidth = widths.sum()
                if (stretchToFillWidth && targetWidth != Constraints.Infinity &&
                    targetWidth > naturalWidth && naturalWidth > 0
                ) {
                    val extra = targetWidth - naturalWidth
                    var distributed = 0
                    widths.forEachIndexed { column, width ->
                        val share = if (column == widths.lastIndex) {
                            extra - distributed
                        } else {
                            (extra.toLong() * width / naturalWidth).toInt()
                        }
                        widths[column] += share
                        distributed += share
                    }
                }
                return widths
            }

            private fun rowHeights(
                cells: List<IntrinsicMeasurable>,
                widths: IntArray,
                intrinsicHeight: (IntrinsicMeasurable, Int) -> Int,
            ): IntArray {
                val heights = IntArray(cells.size / columnCount)
                cells.forEachIndexed { index, cell ->
                    val row = index / columnCount
                    heights[row] = max(heights[row], intrinsicHeight(cell, widths[index % columnCount]))
                }
                return heights
            }

            override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
                // The horizontal scroll removes the maximum width but preserves the viewport's minimum width.
                val widths = columnWidths(measurables, constraints.minWidth) { it.maxIntrinsicWidth(Constraints.Infinity) }
                val heights = rowHeights(measurables, widths) { cell, width -> cell.minIntrinsicHeight(width) }
                val placeables = measurables.mapIndexed { index, cell ->
                    val width = widths[index % columnCount]
                    val row = index / columnCount
                    cell.measure(Constraints.fitPrioritizingWidth(width, width, heights[row], Constraints.Infinity))
                }
                placeables.forEachIndexed { index, cell ->
                    val row = index / columnCount
                    heights[row] = max(heights[row], cell.height)
                }
                return layout(
                    widths.sum().coerceIn(constraints.minWidth, constraints.maxWidth),
                    heights.sum().coerceIn(constraints.minHeight, constraints.maxHeight),
                ) {
                    var y = 0
                    heights.forEachIndexed { row, height ->
                        var x = 0
                        widths.forEachIndexed { column, width ->
                            placeables[row * columnCount + column].placeRelative(x, y)
                            x += width
                        }
                        y += height
                    }
                }
            }

            override fun IntrinsicMeasureScope.minIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int,
            ): Int = columnWidths(measurables) { it.minIntrinsicWidth(height) }.sum()

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                measurables: List<IntrinsicMeasurable>,
                height: Int,
            ): Int = columnWidths(measurables) { it.maxIntrinsicWidth(height) }.sum()

            override fun IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int,
            ): Int {
                val widths = columnWidths(measurables, width) { it.maxIntrinsicWidth(Constraints.Infinity) }
                return rowHeights(measurables, widths) { cell, column -> cell.minIntrinsicHeight(column) }.sum()
            }

            override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                measurables: List<IntrinsicMeasurable>,
                width: Int,
            ): Int {
                val widths = columnWidths(measurables, width) { it.maxIntrinsicWidth(Constraints.Infinity) }
                return rowHeights(measurables, widths) { cell, column -> cell.maxIntrinsicHeight(column) }.sum()
            }
        }
    }

    Layout(
        modifier = modifier
            .clip(shape)
            .then(if (outerBorder != null) Modifier.border(outerBorder, shape) else Modifier)
            .then(if (stretchToFillWidth) Modifier.fillMaxWidth() else Modifier)
            .horizontalScroll(hScroll),
        content = {
            for (column in 0 until columnCount) {
                CellBox(
                    horizontalPadding = cellHorizontalPadding,
                    verticalPadding = cellVerticalPadding,
                    border = cellBorder,
                    bottomDividerColor = null,
                    backgroundShape = headerShapeFor(column),
                    background = headerBackground,
                    alignment = columnAlignments.getOrNull(column) ?: cellAlignment,
                ) {
                    headers.getOrNull(column)?.invoke()
                }
            }
            for ((row, rowCells) in rows.withIndex()) {
                val background = if (zebraStriping && row % 2 == 1) surfaceContainer else Color.Transparent
                val divider = if (row < rowCount - 1) rowDividerColor else null
                for (column in 0 until columnCount) {
                    CellBox(
                        horizontalPadding = cellHorizontalPadding,
                        verticalPadding = cellVerticalPadding,
                        border = cellBorder,
                        bottomDividerColor = divider,
                        backgroundShape = RectangleShape,
                        background = background,
                        alignment = bodyCellAlignment?.invoke(row, column)
                            ?: columnAlignments.getOrNull(column) ?: cellAlignment,
                    ) {
                        rowCells.getOrNull(column)?.invoke()
                    }
                }
            }
        },
        measurePolicy = measurePolicy,
    )
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
