package co.edu.unal.reto10_jm

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

private val approvedColor = Color(0xFF387BF4)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreen(
    viewModel: DashboardViewModel,
    tab: Int,
    selectTab: (Int) -> Unit,
    expanded: Boolean,
    setExpanded: (Boolean) -> Unit,
    modifier: Modifier
) {
    BackHandler(expanded) { setExpanded(false) }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dashboard", style = MaterialTheme.typography.titleLarge)
            if (viewModel.state is DashboardState.Loaded) {
                TextButton(onClick = { setExpanded(!expanded) }) {
                    Text(if (expanded) "Cerrar vista ampliada" else "Ampliar gráfica")
                }
            }
        }
        PrimaryTabRow(selectedTabIndex = tab) {
            listOf("Estados", "Valor aprobado").forEachIndexed { index, label ->
                Tab(selected = index == tab, onClick = { selectTab(index) }, text = { Text(label) })
            }
        }
        when (val state = viewModel.state) {
            DashboardState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            DashboardState.Empty -> DashboardMessage("No hay datos para el período.", viewModel::load)
            is DashboardState.Error -> DashboardMessage(state.message, viewModel::load)
            is DashboardState.Loaded -> DashboardCharts(state.data, tab == 1, expanded)
        }
    }
}

@Composable
private fun DashboardMessage(message: String, retry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message)
            Button(onClick = retry) { Text("Reintentar") }
        }
    }
}

@Composable
private fun DashboardCharts(data: DashboardData, approved: Boolean, expanded: Boolean) {
    var hiddenSeries by rememberSaveable { mutableIntStateOf(0) }
    var selectedIndex by rememberSaveable { mutableIntStateOf(-1) }
    val series = EventSeries.entries.filter { hiddenSeries and (1 shl it.ordinal) == 0 }
    val lines = if (approved) {
        listOf(ChartLine(approvedColor, data.days.map { it.approvedValue.toDouble() }))
    } else {
        series.map { item -> ChartLine(item.color, data.days.map { it.counts[item.ordinal].toDouble() }) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 680.dp
        val suggestRotation = expanded && maxWidth < maxHeight
        val chartHeight = if (wide) (maxHeight - 105.dp).coerceAtLeast(180.dp) else 260.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (approved) "Subtotal + FEE (USD) por día" else "Estados de proyectos por día",
                style = MaterialTheme.typography.titleMedium
            )
            Text("${data.days.first().label} – ${data.days.last().label} · ${data.days.size} días")
            if (suggestRotation) {
                Text("Gira el dispositivo para ver la gráfica con más ancho.")
            }
            val graph: @Composable (Modifier) -> Unit = { chartModifier ->
                Column(chartModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LineChart(
                        lines, data.days.map { it.label }, selectedIndex, { selectedIndex = it },
                        approved, Modifier.fillMaxWidth().height(chartHeight)
                    )
                    val day = data.days.getOrNull(selectedIndex)
                    if (day != null) {
                        val value = if (approved) {
                            usd(day.approvedValue)
                        } else {
                            series.joinToString(" · ") { "${it.label} ${day.counts[it.ordinal]}" }
                        }
                        Text(
                            if (value.isEmpty()) day.label else "${day.label} · $value",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            if (!approved && wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    graph(Modifier.weight(1f))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        EventSeries.entries.forEach { item ->
                            SeriesLegend(item, data.totals[item.ordinal], item in series) {
                                hiddenSeries = hiddenSeries xor (1 shl item.ordinal)
                            }
                        }
                    }
                }
            } else {
                graph(Modifier.fillMaxWidth())
                if (!approved) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        EventSeries.entries.forEach { item ->
                            SeriesLegend(item, data.totals[item.ordinal], item in series) {
                                hiddenSeries = hiddenSeries xor (1 shl item.ordinal)
                            }
                        }
                    }
                }
            }
            if (approved) {
                Text("Total del período · ${usd(data.approvedTotal)}",
                    style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SeriesLegend(item: EventSeries, total: Int, visible: Boolean, toggle: () -> Unit) {
    Row(
        Modifier.toggleable(value = visible, role = Role.Checkbox, onValueChange = { toggle() })
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(color = item.color.copy(alpha = if (visible) 1f else 0.3f), modifier = Modifier.size(9.dp)) {}
        Text(
            "$total ${item.label}",
            style = MaterialTheme.typography.bodySmall,
            color = if (visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private data class ChartLine(val color: Color, val values: List<Double>)

@Composable
private fun LineChart(
    lines: List<ChartLine>,
    dates: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    monetary: Boolean,
    modifier: Modifier
) {
    val measurer = rememberTextMeasurer()
    val textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val highest = lines.maxOfOrNull { it.values.maxOrNull() ?: 0.0 } ?: 0.0
    val lowest = lines.minOfOrNull { it.values.minOrNull() ?: 0.0 } ?: 0.0
    val rawStep = (highest.coerceAtLeast(0.0) - lowest.coerceAtMost(0.0)) / 4
    val magnitude = 10.0.pow(floor(log10(rawStep.coerceAtLeast(0.01))))
    val step = (ceil(rawStep / magnitude) * magnitude).coerceAtLeast(if (monetary) 0.01 else 1.0)
    val lowerTick = floor(lowest.coerceAtMost(0.0) / step).toInt()
    val upperTick = ceil(highest.coerceAtLeast(step) / step).toInt()
    val tickLabels = (lowerTick..upperTick).map { tick ->
        measurer.measure(axisLabel(tick * step, monetary), textStyle)
    }
    val left = with(androidx.compose.ui.platform.LocalDensity.current) {
        tickLabels.maxOf { it.size.width }.toFloat().toDp() + 10.dp
    }
    Canvas(
        modifier.semantics {
            contentDescription = if (monetary) "Gráfica de valor aprobado por día" else "Gráfica de estados por día"
        }.pointerInput(dates.size, left) {
            detectTapGestures { point ->
                val width = (size.width - left.toPx() - 22.dp.toPx()).coerceAtLeast(1f)
                val fraction = ((point.x - left.toPx()) / width).coerceIn(0f, 1f)
                onSelect((fraction * dates.lastIndex).roundToInt())
            }
        }
    ) {
        val start = left.toPx()
        val end = (size.width - 22.dp.toPx()).coerceAtLeast(start + 1f)
        val top = 12.dp.toPx()
        val bottom = size.height - 26.dp.toPx()
        fun x(index: Int) = start + index.toFloat() / dates.lastIndex.coerceAtLeast(1) * (end - start)
        fun y(value: Double) = bottom - ((value / step - lowerTick) / (upperTick - lowerTick))
            .toFloat() * (bottom - top)

        tickLabels.forEachIndexed { index, label ->
            val lineY = y((index + lowerTick) * step)
            drawLine(grid, Offset(start, lineY), Offset(end, lineY))
            drawText(label,
                topLeft = Offset(start - label.size.width - 5.dp.toPx(), lineY - label.size.height / 2f))
        }

        val labelSlots = ((end - start) / 46.dp.toPx()).toInt().coerceAtLeast(1)
        val stride = ceil(dates.lastIndex.toDouble() / labelSlots).toInt().coerceAtLeast(1)
        for (index in dates.indices) {
            if (index != dates.lastIndex && (index % stride != 0 || dates.lastIndex - index < stride && index != 0)) continue
            val label = measurer.measure(dates[index], textStyle)
            drawText(label, topLeft = Offset(x(index) - label.size.width / 2f, bottom + 4.dp.toPx()))
        }

        if (selectedIndex in dates.indices) {
            val selectedX = x(selectedIndex)
            drawLine(grid, Offset(selectedX, top), Offset(selectedX, bottom), strokeWidth = 2.dp.toPx())
        }
        lines.forEach { line ->
            val path = Path()
            line.values.forEachIndexed { index, value ->
                if (index == 0) path.moveTo(x(index), y(value)) else path.lineTo(x(index), y(value))
                if (index == selectedIndex || line.values.size == 1) {
                    drawCircle(line.color, 3.dp.toPx(), Offset(x(index), y(value)))
                }
            }
            drawPath(path, line.color, style = Stroke(2.dp.toPx()))
        }
    }
}

private fun axisLabel(value: Double, monetary: Boolean): String {
    val format = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CO")).apply {
        maximumFractionDigits = if (monetary) 2 else 0
    }
    return if (monetary && kotlin.math.abs(value) >= 1_000) {
        "${format.format(value / 1_000)} mil"
    } else {
        format.format(value)
    }
}

private fun usd(value: BigDecimal): String =
    "USD ${NumberFormat.getNumberInstance(Locale.US).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }.format(value)}"

