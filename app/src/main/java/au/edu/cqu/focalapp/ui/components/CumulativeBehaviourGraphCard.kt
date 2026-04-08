package au.edu.cqu.focalapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import au.edu.cqu.focalapp.domain.model.GraphBehaviourCategory
import au.edu.cqu.focalapp.ui.AnimalGraphGroupUiState
import au.edu.cqu.focalapp.ui.BehaviourGraphUiState
import au.edu.cqu.focalapp.ui.palette
import kotlin.math.max

@Composable
fun CumulativeBehaviourGraphCard(
    graph: BehaviourGraphUiState,
    isPortraitTablet: Boolean,
    modifier: Modifier = Modifier
) {
    val contentPadding = if (isPortraitTablet) 24.dp else 16.dp
    val chartHeight = if (isPortraitTablet) 300.dp else 220.dp

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Cumulative behaviour graph",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "Grouped bars show minutes for Walking, Grazing, and Idle across saved tablet sessions. Blue, Green, and Yellow always stay visible here as a focus guide, and the current session updates live.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            GraphLegend()

            if (graph.hasGroups) {
                GroupedBarChart(
                    groups = graph.groups,
                    yAxisMaxMinutes = graph.yAxisMaxMinutes,
                    chartHeight = chartHeight
                )
            } else {
                Text(
                    text = "Select at least one animal to show the graph.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GraphLegend() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GraphLegendItem(
            label = GraphBehaviourCategory.WALKING.label,
            color = graphCategoryColor(GraphBehaviourCategory.WALKING)
        )
        GraphLegendItem(
            label = GraphBehaviourCategory.GRAZING.label,
            color = graphCategoryColor(GraphBehaviourCategory.GRAZING)
        )
        GraphLegendItem(
            label = GraphBehaviourCategory.IDLE.label,
            color = graphCategoryColor(GraphBehaviourCategory.IDLE)
        )
    }
}

@Composable
private fun GraphLegendItem(
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            modifier = Modifier
                .width(12.dp)
                .height(12.dp),
            color = color,
            shape = CircleShape
        ) {}

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GroupedBarChart(
    groups: List<AnimalGraphGroupUiState>,
    yAxisMaxMinutes: Int,
    chartHeight: androidx.compose.ui.unit.Dp
) {
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val labelColor = MaterialTheme.colorScheme.onSurface
    val topLabel = remember(yAxisMaxMinutes) { yAxisMaxMinutes.toString() }
    val midLabel = remember(yAxisMaxMinutes) { max(0, yAxisMaxMinutes / 2).toString() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom
    ) {
        Column(
            modifier = Modifier
                .height(chartHeight)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            Text(topLabel, style = MaterialTheme.typography.labelSmall, color = axisTextColor)
            Text(midLabel, style = MaterialTheme.typography.labelSmall, color = axisTextColor)
            Text("0", style = MaterialTheme.typography.labelSmall, color = axisTextColor)
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val groupCount = groups.size.coerceAtLeast(1)
                    val groupGap = 18.dp.toPx()
                    val barGap = 6.dp.toPx()
                    val availableWidth = size.width - groupGap * (groupCount - 1)
                    val groupWidth = availableWidth / groupCount
                    val barWidth = ((groupWidth - barGap * 2f) / 3f).coerceAtLeast(8.dp.toPx())
                    val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())

                    val chartMax = yAxisMaxMinutes.toFloat().coerceAtLeast(1f)
                    val baselineY = size.height
                    val midY = size.height / 2f

                    drawLine(
                        color = outlineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, baselineY),
                        end = androidx.compose.ui.geometry.Offset(size.width, baselineY),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = outlineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, midY),
                        end = androidx.compose.ui.geometry.Offset(size.width, midY),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = outlineColor,
                        start = androidx.compose.ui.geometry.Offset(0f, 0f),
                        end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawRect(
                        color = outlineColor,
                        size = size,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    groups.forEachIndexed { groupIndex, group ->
                        val groupStart = groupIndex * (groupWidth + groupGap)
                        group.bars.forEachIndexed { barIndex, bar ->
                            val heightFraction = (bar.minutes / chartMax).coerceIn(0f, 1f)
                            val barHeight = size.height * heightFraction
                            val left = groupStart + barIndex * (barWidth + barGap)
                            val top = baselineY - barHeight

                            drawRoundRect(
                                color = graphCategoryColor(bar.category),
                                topLeft = androidx.compose.ui.geometry.Offset(left, top),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                cornerRadius = radius
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                groups.forEach { group ->
                    val palette = group.trackedAnimal.animalColor.palette()
                    Text(
                        modifier = Modifier.weight(1f),
                        text = group.trackedAnimal.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        color = palette.borderColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = "Minutes",
                style = MaterialTheme.typography.labelMedium,
                color = labelColor
            )
        }
    }
}

private fun graphCategoryColor(category: GraphBehaviourCategory): Color {
    return when (category) {
        GraphBehaviourCategory.WALKING -> Color(0xFF4FC3F7)
        GraphBehaviourCategory.GRAZING -> Color(0xFF7ED957)
        GraphBehaviourCategory.IDLE -> Color(0xFFFFC857)
    }
}
