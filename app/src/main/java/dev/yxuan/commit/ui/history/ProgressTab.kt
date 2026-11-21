package dev.yxuan.commit.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.LineCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.data.columnSeries
import com.patrykandpatrick.vico.compose.cartesian.data.lineSeries
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.component.ShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import dev.yxuan.commit.data.db.dao.ExerciseSessionPoint
import dev.yxuan.commit.domain.ProgressPoint
import dev.yxuan.commit.domain.Stats
import dev.yxuan.commit.ui.format.WorkoutFormat
import dev.yxuan.commit.ui.settings.SectionTitle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Session dates for the x-axis labels and PB flags for the points, passed to the chart via extras. */
private val DatesKey = ExtraStore.Key<List<Long>>()
private val PbKey = ExtraStore.Key<Set<Int>>()

/** Per-exercise progress (F8.6): best weight per session with PB markers, and volume per session. */
@Composable
fun ProgressTab(points: List<ExerciseSessionPoint>) {
    val names = remember(points) { points.map { it.exerciseName }.distinctBy { it.lowercase() }.sorted() }
    if (names.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("Log a few workouts to see your progress.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val exercise = names.firstOrNull { it.equals(selected, ignoreCase = true) } ?: names.first()
    val progress = remember(points, exercise) { Stats.progress(points.filter { it.exerciseName.equals(exercise, ignoreCase = true) }) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEach { name ->
                    FilterChip(selected = name == exercise, onClick = { selected = name }, label = { Text(name) })
                }
            }
        }
        item { SectionTitle("Best weight (kg)") }
        item { WeightChart(progress) }
        item { SectionTitle("Volume per session (kg)") }
        item { VolumeChart(progress) }
        val pbs = progress.filter { it.isPersonalBest }.reversed()
        if (pbs.isNotEmpty()) {
            item { SectionTitle("Personal bests") }
            items(pbs, key = { it.point.sessionId }) { p ->
                ListItem(
                    headlineContent = { Text("${WorkoutFormat.weight(p.point.bestWeight)} × ${p.point.bestReps}") },
                    supportingContent = { Text(formatDate(p.point.startedAt)) },
                )
            }
        }
    }
}

@Composable
private fun WeightChart(progress: List<ProgressPoint>) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(progress) {
        producer.runTransaction {
            lineSeries { series(progress.map { it.point.bestWeight }) }
            extras { store ->
                store[DatesKey] = progress.map { it.point.startedAt }
                store[PbKey] = progress.withIndex().filter { it.value.isPersonalBest }.map { it.index }.toSet()
            }
        }
    }
    val color = MaterialTheme.colorScheme.primary
    val pbColor = MaterialTheme.colorScheme.tertiary
    // Every session gets a small dot; personal bests get a larger dot in the accent colour.
    val pointProvider = remember(color, pbColor) {
        val normal = LineCartesianLayer.Point(ShapeComponent(Fill(color), CircleShape), 6.dp)
        val pb = LineCartesianLayer.Point(ShapeComponent(Fill(pbColor), CircleShape), 12.dp)
        object : LineCartesianLayer.PointProvider {
            override fun getPoint(entry: LineCartesianLayerModel.Entry, seriesIndex: Int, extraStore: ExtraStore) =
                if (entry.x.toInt() in extraStore.getOrNull(PbKey).orEmpty()) pb else normal

            override fun getLargestPoint(extraStore: ExtraStore) = pb
        }
    }
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(
                LineCartesianLayer.LineProvider.series(
                    LineCartesianLayer.rememberLine(
                        fill = LineCartesianLayer.LineFill.single(Fill(color)),
                        pointProvider = pointProvider,
                    )
                )
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dateFormatter),
        ),
        modelProducer = producer,
        modifier = Modifier.fillMaxWidth().height(220.dp).padding(horizontal = 16.dp),
    )
}

@Composable
private fun VolumeChart(progress: List<ProgressPoint>) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(progress) {
        producer.runTransaction {
            columnSeries { series(progress.map { it.point.volume }) }
            extras { store -> store[DatesKey] = progress.map { it.point.startedAt } }
        }
    }
    val color = MaterialTheme.colorScheme.secondary
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer.ColumnProvider.series(
                    rememberLineComponent(fill = Fill(color), thickness = 12.dp)
                )
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = dateFormatter),
        ),
        modelProducer = producer,
        modifier = Modifier.fillMaxWidth().height(200.dp).padding(horizontal = 16.dp),
    )
}

/** x is the session index; label it with the session's date ("21 Nov"). */
private val dateFormatter = CartesianValueFormatter { context, value, _ ->
    val dates = context.model.extraStore.getOrNull(DatesKey).orEmpty()
    dates.getOrNull(value.toInt())?.let { formatDate(it, "d MMM") } ?: " "
}

private fun formatDate(millis: Long, pattern: String = "EEE d MMM yyyy"): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(pattern))
