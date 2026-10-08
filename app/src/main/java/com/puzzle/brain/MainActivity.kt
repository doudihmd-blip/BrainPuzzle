package com.puzzle.brain

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
    Color(0xFFFFEB3B), // أصفر
    Color(0xFF795548)  // بني
)

// خوارزمية الصعوبة السريعة (تزداد التعقيد كل 5 مستويات)
fun generateLevel(levelNum: Int, boardSizePx: Float): GeneratedLevel {
    val padding = boardSizePx * 0.12f
    val playable = boardSizePx - (padding * 2)

    val gridSize = when {
        levelNum <= 5 -> 4
        levelNum <= 15 -> 5
        levelNum <= 30 -> 6
        levelNum <= 60 -> 7
        else -> 8
    }
    val cellSize = playable / (gridSize - 1)

    val pairCount = when {
        levelNum <= 5 -> 3
        levelNum <= 15 -> 4
        levelNum <= 35 -> 5
        levelNum <= 70 -> 6
        else -> 7
    }.coerceAtMost(GameColors.size)

    val random = Random(levelNum * 9173 + 31)
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
        } while ((startCell == endCell || usedCells.contains(startCell) || usedCells.contains(endCell)) && attempts < 150)

        usedCells.add(startCell)
        usedCells.add(endCell)

        val startOffset = Offset(padding + startCell.first * cellSize, padding + startCell.second * cellSize)
        val endOffset = Offset(padding + endCell.first * cellSize, padding + endCell.second * cellSize)

        pairs.add(DotPair(i + 1, GameColors[i % GameColors.size], startOffset, endOffset))
    }

    val iq = 110 + (levelNum * 1.8).toInt()
    return GeneratedLevel(levelNum, iq, pairs)
}

// دالة فحص تقاطع قطعتين مستقيمتين
fun segmentsIntersect(p1: Offset, p2: Offset, p3: Offset, p4: Offset): Boolean {
    fun ccw(a: Offset, b: Offset, c: Offset): Boolean {
        return (c.y - a.y) * (b.x - a.x) > (b.y - a.y) * (c.x - a.x)
    }
    return (ccw(p1, p3, p4) != ccw(p2, p3, p4)) && (ccw(p1, p2, p3) != ccw(p1, p2, p4))
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

    var currentLevelNumber by remember { mutableStateOf(prefs.getInt("saved_level", 28)) }
    val boardSizePx = 800f
    val currentLevel = remember(currentLevelNumber) { generateLevel(currentLevelNumber, boardSizePx) }

    val completedPaths = remember { mutableStateMapOf<Int, List<Offset>>() }
    var activePairId by remember { mutableStateOf<Int?>(null) }
    var activePath by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var levelPassed by remember { mutableStateOf(false) }

    // أنيميشن رقص وتصفيق الشخصية
    val infiniteTransition = rememberInfiniteTransition(label = "ClapDance")
    val danceRotate by infiniteTransition.animateFloat(
        initialValue = -12f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(
            animation = tween(220, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Rotate"
    )
    val danceBounce by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(220, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Bounce"
    )

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
        // شريط الرأس مع صورة الشخص وعداد الـ IQ
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF)),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // صورة الشخصية مع تأثير التصفيق عند الفوز
                Box(contentAlignment = Alignment.Center) {
                    val resId = context.resources.getIdentifier("character", "drawable", context.packageName)
                    val modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .border(3.dp, if (levelPassed) Color(0xFFFFD700) else Color.White, CircleShape)
                        .then(
                            if (levelPassed) Modifier.scale(danceBounce).rotate(danceRotate)
                            else Modifier
                        )

                    if (resId != 0) {
                        Image(
                            painter = painterResource(id = resId),
                            contentDescription = "Character",
                            modifier = modifier,
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = modifier.background(Color(0xFF37474F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(if (levelPassed) "🥳" else "😎", fontSize = 32.sp)
                        }
                    }

                    if (levelPassed) {
                        Text(
                            text = "👏",
                            fontSize = 24.sp,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 6.dp, y = 6.dp)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "المستوى ${currentLevel.number}",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "صل ${currentLevel.pairs.size} مسارات دون تقاطع!",
                        color = Color(0xFFFFEB3B),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    color = Color(0xFFFFC107),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "IQ ${currentLevel.iqScore}",
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF1A1A1A),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // رقعة اللعب
        Box(
            modifier = Modifier
                .size(340.dp)
                .background(Color.White, shape = RoundedCornerShape(24.dp))
                .padding(8.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(currentLevelNumber) {
                        val scale = size.width / boardSizePx
                        val dotRadius = 34f * scale

                        detectDragGestures(
                            onDragStart = { offset ->
                                for (pair in currentLevel.pairs) {
                                    val scaledStart = pair.start * scale
                                    val scaledEnd = pair.end * scale
                                    if (hypot(offset.x - scaledStart.x, offset.y - scaledStart.y) <= dotRadius + 28f) {
                                        activePairId = pair.id
                                        activePath = listOf(scaledStart)
                                        completedPaths.remove(pair.id)
                                        break
                                    } else if (hypot(offset.x - scaledEnd.x, offset.y - scaledEnd.y) <= dotRadius + 28f) {
                                        activePairId = pair.id
                                        activePath = listOf(scaledEnd)
                                        completedPaths.remove(pair.id)
                                        break
                                    }
                                }
                            },
                            onDrag = { change, _ ->
                                if (activePairId != null) {
                                    val newPt = change.position
                                    val lastPt = activePath.lastOrNull()

                                    // فحص تقاطع الخط الحالي مع أي خط مكتمل سابقاً
                                    var intersects = false
                                    if (lastPt != null && hypot(newPt.x - lastPt.x, newPt.y - lastPt.y) > 4f) {
                                        for ((otherId, otherPts) in completedPaths) {
                                            if (otherId == activePairId) continue
                                            for (j in 0 until otherPts.size - 1) {
                                                if (segmentsIntersect(lastPt, newPt, otherPts[j], otherPts[j + 1])) {
                                                    intersects = true
                                                    break
                                                }
                                            }
                                            if (intersects) break
                                        }
                                    }

                                    if (!intersects) {
                                        activePath = activePath + newPt
                                    } else {
                                        // كسر الخط إذا حاول المرور فوق خط آخر!
                                        activePath = emptyList()
                                        activePairId = null
                                    }
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

                                        val target = if (hypot(first.x - scaledStart.x, first.y - scaledStart.y) < 20f) scaledEnd else scaledStart
                                        if (hypot(last.x - target.x, last.y - target.y) <= (34f * scale) + 32f) {
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
                                color = pair.color.copy(alpha = 0.9f),
                                start = pts[i],
                                end = pts[i + 1],
                                strokeWidth = 15f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // رسم المسار النشط
                if (activePairId != null && activePath.size > 1) {
                    currentLevel.pairs.find { it.id == activePairId }?.let { pair ->
                        for (i in 0 until activePath.size - 1) {
                            drawLine(
                                color = pair.color,
                                start = activePath[i],
                                end = activePath[i + 1],
                                strokeWidth = 15f,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                // رسم النقاط
                currentLevel.pairs.forEach { pair ->
                    listOf(pair.start * scale, pair.end * scale).forEach { pos ->
                        drawCircle(color = Color.White, radius = 32f * scale, center = pos)
                        drawCircle(color = pair.color, radius = 26f * scale, center = pos)
                        drawCircle(color = Color.White.copy(alpha = 0.6f), radius = 7f * scale, center = pos - Offset(5f * scale, 5f * scale))
                    }
                }
            }
        }

        // أزرار التحكم والاحتفال
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            if (levelPassed) {
                Text(
                    text = "👏 أحسنت يا بطل! تم إكمال المستوى! 🎉",
                    color = Color(0xFFFFEB3B),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 10.dp)
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
