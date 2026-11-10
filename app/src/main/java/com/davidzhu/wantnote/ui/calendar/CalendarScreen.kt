package com.davidzhu.wantnote.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.davidzhu.wantnote.data.Dates
import com.davidzhu.wantnote.data.DayTask
import com.davidzhu.wantnote.data.Note
import com.davidzhu.wantnote.data.SearchText
import com.davidzhu.wantnote.data.YearMonth
import com.davidzhu.wantnote.ui.LocalNotes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onOpenNote: (String) -> Unit) {
    val repository = LocalNotes.current
    val items by repository.observeAllWithBlocks().collectAsStateWithLifecycle(initialValue = emptyList())
    var month by remember { mutableStateOf(Dates.currentYearMonth()) }
    val todayStart = remember { Dates.startOfDay(System.currentTimeMillis()) }
    var selectedDay by remember { mutableStateOf(todayStart) }
    val scope = rememberCoroutineScope()

    val notesOnDay = remember(items, selectedDay) {
        items.map { it.note }.filter { Dates.belongsToDay(it, selectedDay) }
    }
    val tasksOnDay = remember(items, selectedDay) {
        repository.tasksOnDay(items, selectedDay)
    }
    val markedDays = remember(items, month) {
        items.map { it.note }.flatMap { listOf(Dates.startOfDay(it.createdAt), Dates.startOfDay(it.updatedAt)) }.toSet()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("日历") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            MonthHeader(month = month, onPrev = { month = month.previous() }, onNext = { month = month.next() })
            WeekdayHeader()
            MonthGrid(
                month = month,
                selectedDay = selectedDay,
                todayStart = todayStart,
                markedDays = markedDays,
                onSelect = { selectedDay = it },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = Dates.formatDay(selectedDay),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("当天笔记", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (notesOnDay.isEmpty()) {
                Text(
                    text = "这一天还没有笔记。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                notesOnDay.forEach { note ->
                    DayNoteRow(note = note, onClick = { onOpenNote(note.id) })
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("当天任务", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (tasksOnDay.isEmpty()) {
                Text(
                    text = "这一天还没有任务。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                tasksOnDay.forEach { task ->
                    DayTaskRow(
                        task = task,
                        onToggle = {
                            scope.launch {
                                repository.setTaskChecked(task.block.id, !task.block.checked)
                            }
                        },
                        onOpenNote = { onOpenNote(task.note.id) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "上个月")
        }
        Text(month.label(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "下个月")
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Dates.weekdayLabels().forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selectedDay: Long,
    todayStart: Long,
    markedDays: Set<Long>,
    onSelect: (Long) -> Unit,
) {
    val cells = remember(month) { Dates.daysInMonthGrid(month) }
    val rows = cells.chunked(7)
    Column {
        rows.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (day != null) {
                            val selected = day == selectedDay
                            val isToday = day == todayStart
                            val marked = day in markedDays
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .then(
                                        if (selected) {
                                            Modifier.background(MaterialTheme.colorScheme.primary)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .then(
                                        if (isToday && !selected) {
                                            Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        } else {
                                            Modifier
                                        },
                                    )
                                    .clickable { onSelect(day) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val dayCal = java.util.Calendar.getInstance().apply { timeInMillis = day }
                                    Text(
                                        text = dayCal.get(java.util.Calendar.DAY_OF_MONTH).toString(),
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onBackground
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                    )
                                    if (marked) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 1.dp)
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (selected) {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    } else {
                                                        MaterialTheme.colorScheme.primary
                                                    },
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayNoteRow(note: Note, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = SearchText.displayTitle(note),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = Dates.formatTime(note.updatedAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun DayTaskRow(
    task: DayTask,
    onToggle: () -> Unit,
    onOpenNote: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenNote)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.block.checked, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.block.text.ifBlank { "未命名任务" },
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.block.checked) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = SearchText.displayTitle(task.note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
