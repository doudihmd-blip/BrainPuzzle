package com.puzzle.brain

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot

data class DotPair(
    val id: Int,
    val color: Color,
    val start: Offset,
    val end: Offset
)

data class Level(
    val number: Int,
    val iqScore: Int,
    val pairs: List<DotPair>
)

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
    // تعريف المستويات المتدرجة
    val levels = remember {
        listOf(
            Level(
                number = 1,
                iqScore = 110,
                pairs = listOf(
                    DotPair(1, Color(0xFF4CAF50), Offset(150f, 150f), Offset(650f, 650f))
                )
            ),
            Level(
                number = 2,
                iqScore = 125,
                pairs = listOf(
                    DotPair(1, Color(0xFF4CAF50), Offset(150f, 150f), Offset(650f, 150f)),
                    DotPair(2, Color(0xFFE91E63), Offset(150f, 650f), Offset(650f, 650f))
                )
            ),
            Level(
                number = 3,
                iqScore = 140,
                pairs = listOf(
                    DotPair(1, Color(0xFF4CAF50), Offset(150f, 150f), Offset(650f, 650f)),
                    DotPair(2, Color(0xFF2196F3), Offset(650f, 150f), Offset(150f, 650f))
                )
            ),
            Level(
                number = 4,
                iqScore = 160,
                pairs = listOf(
                    DotPair(1, Color(0xFF4CAF50), Offset(150f, 150f), Offset(400f, 400f)),
                    DotPair(2, Color(0xFF2196F3), Offset(650f, 150f), Offset(650f, 650f)),
                    DotPair(3, Color(0xFFE91E63), Offset(150f, 650f), Offset(400f, 650f))
                )
            )
        )
    }

    var currentLevelIndex by remember { mutableStateOf(0) }
    val currentLevel = levels[currentLevelIndex]

    // حالة الخطوط المكتملة والخط قيد الرسم
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

    LaunchedEffect(currentLevelIndex) {
        resetLevel()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364))
                )
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // شريط الرأس: IQ ورقم المستوى
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0x33FFFFFF)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                        text = "صل الألوان المتطابقة دون تقاطع",
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
                    .pointerInput(currentLevelIndex) {
                        val dotRadius = 38f
                        detectDragGestures(
                            onDragStart = { offset ->
                                for (pair in currentLevel.pairs) {
                                    val dStart = hypot(offset.x - pair.start.x, offset.y - pair.start.y)
                                    val dEnd = hypot(offset.x - pair.end.x, offset.y - pair.end.y)
                                    if (dStart <= dotRadius + 25f) {
                                        activePairId = pair.id
                                        activePath = listOf(pair.start)
                                        completedPaths.remove(pair.id)
                                        break
                                    } else if (dEnd <= dotRadius + 25f) {
                                        activePairId = pair.id
                                        activePath = listOf(pair.end)
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
                                    val pair = currentLevel.pairs.first { it.id == pairId }
                                    val last = activePath.last()
                                    val first = activePath.first()
                                    
                                    val target = if (hypot(first.x - pair.start.x, first.y - pair.start.y) < 10f) pair.end else pair.start
                                    val dist = hypot(last.x - target.x, last.y - target.y)

                                    if (dist <= dotRadius + 30f) {
                                        completedPaths[pairId] = activePath + target
                                        if (completedPaths.size == currentLevel.pairs.size) {
                                            levelPassed = true
                                        }
                                    }
                                }
                                activePairId = null
                                activePath = emptyList()
                            }
                        )
                    }
            ) {
                // رسم الخطوط المكتملة
                completedPaths.forEach { (id, pts) ->
                    val pair = currentLevel.pairs.first { it.id == id }
                    for (i in 0 until pts.size - 1) {
                        drawLine(
                            color = pair.color.copy(alpha = 0.85f),
                            start = pts[i],
                            end = pts[i + 1],
                            strokeWidth = 18f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // رسم الخط النشط الحالي
                if (activePairId != null && activePath.size > 1) {
                    val pair = currentLevel.pairs.first { it.id == activePairId }
                    for (i in 0 until activePath.size - 1) {
                        drawLine(
                            color = pair.color,
                            start = activePath[i],
                            end = activePath[i + 1],
                            strokeWidth = 18f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // رسم النقاط
                currentLevel.pairs.forEach { pair ->
                    listOf(pair.start, pair.end).forEach { pos ->
                        drawCircle(color = Color.White, radius = 38f, center = pos)
                        drawCircle(color = pair.color, radius = 32f, center = pos)
                        drawCircle(color = Color.White.copy(alpha = 0.6f), radius = 10f, center = pos - Offset(8f, 8f))
                    }
                }
            }
        }

        // أزرار التحكم وحالة النجاح
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 20.dp)
        ) {
            if (levelPassed) {
                Text(
                    text = "🎉 عبقري! تم حل المستوى بنجاح!",
                    color = Color(0xFFFFEB3B),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Button(
                    onClick = {
                        if (currentLevelIndex < levels.size - 1) {
                            currentLevelIndex++
                        } else {
                            currentLevelIndex = 0
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                    shape = RoundedCornerShape(25.dp),
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(50.dp)
                ) {
                    Text(
                        text = if (currentLevelIndex < levels.size - 1) "المستوى التالي ➔" else "إعادة من البداية",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                OutlinedButton(
                    onClick = { resetLevel() },
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(48.dp)
                ) {
                    Text("إعادة المحاولة", fontSize = 16.sp)
                }
            }
        }
    }
}
