package com.puzzle.brain

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.random.Random

data class DotPair(
    val id: Int,
    val color: Color,
    val start: Offset,
    val end: Offset
)

data class GeneratedLevel(
    val number: Int,
    val iqScore: Int,
    val pairs: List<DotPair>
)

val GameColors = listOf(
    Color(0xFF4CAF50), // أخضر
    Color(0xFFE91E63), // وردي
    Color(0xFF2196F3), // أزرق
    Color(0xFFFF9800), // برتقالي
    Color(0xFF9C27B0), // بنفسجي
    Color(0xFF00BCD4), // سماوي
    Color(0xFFFFEB3B)  // أصفر
)

// مولد مستويات احترافي ينتج مستويات غير محدودة بدقة
fun generateLevel(levelNum: Int, boardSizePx: Float): GeneratedLevel {
    val padding = boardSizePx * 0.15f
    val playable = boardSizePx - (padding * 2)
    val gridSize = when {
        levelNum <= 20 -> 4
        levelNum <= 80 -> 5
        levelNum <= 150 -> 6
        else -> 7
    }
    val cellSize = playable / (gridSize - 1)
    
    val pairCount = when {
        levelNum <= 10 -> 2
        levelNum <= 40 -> 3
        levelNum <= 100 -> 4
        levelNum <= 180 -> 5
        else -> 6
    }.coerceAtMost(GameColors.size)

    val random = Random(levelNum * 7919) // خوارزمية ثابتة لضمان نفس المستوى لكل اللاعبين
    val usedCells = mutableSetOf<Pair<Int, Int>>()
    val pairs = mutableListOf<DotPair>()

    for (i in 0 until pairCount) {
        var startCell: Pair<Int, Int>
        var endCell: Pair<Int, Int>
        var attempts = 0

        do {
            startCell = Pair(random.nextInt(gridSize), random.nextInt(gridSize))
            endCell = Pair(random.nextInt(gridSize), random.nextInt(gridSize))
            attempts++
        } while ((startCell == endCell || usedCells.contains(startCell) || usedCells.contains(endCell)) && attempts < 100)

        usedCells.add(startCell)
        usedCells.add(endCell)

        val startOffset = Offset(padding + startCell.first * cellSize, padding + startCell.second * cellSize)
        val endOffset = Offset(padding + endCell.first * cellSize, padding + endCell.second * cellSize)

        pairs.add(DotPair(i + 1, GameColors[i % GameColors.size], startOffset, endOffset))
    }

    val iq = 100 + (levelNum * 0.6).toInt()
    return GeneratedLevel(levelNum, iq, pairs)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PuzzleGameApp()
            }
        }
    }
}

@Composable
fun PuzzleGameApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("brain_puzzle_prefs", Context.MODE_PRIVATE) }
    
    var currentLevelNumber by remember { mutableStateOf(prefs.getInt("saved_level", 1)) }
    val boardSizePx = 800f
    val currentLevel = remember(currentLevelNumber) { generateLevel(currentLevelNumber, boardSizePx) }

    val completedPaths = remember { mutableStateMapOf<Int, List<Offset>>() }
    var activePairId by remember { mutableStateOf<Int?>(null) }
    var activePath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var levelPassed by remember { mutableStateOf(false) }

    fun resetLevel() {
        completedPaths.clear()
        activePairId = null
        activePath = emptyList()
        levelPassed = false
    }

    LaunchedEffect(currentLevelNumber) {
        resetLevel()
        prefs.edit().putInt("saved_level", currentLevelNumber).apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // شريط المستوى والـ IQ
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF)),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "المستوى ${currentLevel.number}",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "صل ${currentLevel.pairs.size} ألوان دون أن تتقاطع",
                        color = Color(0xFFB0BEC5),
                        fontSize = 13.sp
                    )
                }

                Surface(
                    color = Color(0xFFFFC107),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "IQ = ${currentLevel.iqScore}",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1A1A1A),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // رقعة اللعب مع اللمس
        Box(
            modifier = Modifier
                .size(330.dp)
                .background(Color.White, shape = RoundedCornerShape(24.dp))
                .padding(6.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentLevelNumber) {
                        val scale = size.width / boardSizePx
                        val dotRadius = 36f * scale

                        detectDragGestures(
                            onDragStart = { offset ->
                                for (pair in currentLevel.pairs) {
                                    val scaledStart = pair.start * scale
                                    val scaledEnd = pair.end * scale
                                    if (hypot(offset.x - scaledStart.x, offset.y - scaledStart.y) <= dotRadius + 25f) {
                                        activePairId = pair.id
                                        activePath = listOf(scaledStart)
                                        completedPaths.remove(pair.id)
                                        break
                                    } else if (hypot(offset.x - scaledEnd.x, offset.y - scaledEnd.y) <= dotRadius + 25f) {
                                        activePairId = pair.id
                                        activePath = listOf(scaledEnd)
                                        completedPaths.remove(pair.id)
                                        break
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                if (activePairId != null) {
                                    activePath = activePath + change.position
                                }
                            },
                            onDragEnd = {
                                val pairId = activePairId
                                if (pairId != null && activePath.isNotEmpty()) {
                                    val scale = size.width / boardSizePx
                                    val pair = currentLevel.pairs.find { it.id == pairId }
                                    if (pair != null) {
                                        val scaledStart = pair.start * scale
                                        val scaledEnd = pair.end * scale
                                        val last = activePath.last()
                                        val first = activePath.first()

                                        val target = if (hypot(first.x - scaledStart.x, first.y - scaledStart.y) < 15f) scaledEnd else scaledStart
                                        if (hypot(last.x - target.x, last.y - target.y) <= (36f * scale) + 30f) {
                                            completedPaths[pairId] = activePath + target
                                            if (completedPaths.size == currentLevel.pairs.size) {
                                                levelPassed = true
                                            }
                                        }
                                    }
                                }
                                activePairId = null
                                activePath = emptyList()
                            }
                        )
                    }
            ) {
                val scale = size.width / boardSizePx

                // رسم المسارات المكتملة
                completedPaths.forEach { (id, pts) ->
                    currentLevel.pairs.find { it.id == id }?.let { pair ->
                        for (i in 0 until pts.size - 1) {
                            drawLine(
                                color = pair.color.copy(alpha = 0.85f),
                                start = pts[i],
                                end = pts[i + 1],
                                strokeWidth = 16f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // رسم المسار قيد السحب
                if (activePairId != null && activePath.size > 1) {
                    currentLevel.pairs.find { it.id == activePairId }?.let { pair ->
                        for (i in 0 until activePath.size - 1) {
                            drawLine(
                                color = pair.color,
                                start = activePath[i],
                                end = activePath[i + 1],
                                strokeWidth = 16f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // رسم النقاط الملونة
                currentLevel.pairs.forEach { pair ->
                    listOf(pair.start * scale, pair.end * scale).forEach { pos ->
                        drawCircle(color = Color.White, radius = 34f * scale, center = pos)
                        drawCircle(color = pair.color, radius = 28f * scale, center = pos)
                        drawCircle(color = Color.White.copy(alpha = 0.6f), radius = 8f * scale, center = pos - Offset(6f * scale, 6f * scale))
                    }
                }
            }
        }

        // أزرار التحكم وحفظ التقدم
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            if (levelPassed) {
                Text(
                    text = "🎉 عبقري! تجاوزت المستوى ${currentLevel.number}",
                    color = Color(0xFFFFEB3B),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Button(
                    onClick = { currentLevelNumber++ },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(25.dp),
                    modifier = Modifier.fillMaxWidth(0.85f).height(52.dp)
                ) {
                    Text("المستوى التالي (${currentLevelNumber + 1}) ➔", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    OutlinedButton(
                        onClick = { resetLevel() },
                        shape = RoundedCornerShape(25.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Text("إعادة المحاولة", fontSize = 15.sp)
                    }

                    if (currentLevelNumber > 1) {
                        OutlinedButton(
                            onClick = { currentLevelNumber-- },
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB0BEC5)),
                            modifier = Modifier.weight(0.7f).height(48.dp)
                        ) {
                            Text("السابق", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
