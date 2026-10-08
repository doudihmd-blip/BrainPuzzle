package com.puzzle.brain

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.hypot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GameScreen()
            }
        }
    }
}

@Composable
fun GameScreen() {
    val context = LocalContext.current
    val pathPoints = remember { mutableStateListOf<Offset>() }
    var isDrawing by remember { mutableStateOf(false) }

    val startDot = Offset(120f, 120f)
    val endDot = Offset(700f, 700f)
    val dotRadius = 35f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E3C72))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "المستوى 1: صل النقاط!",
            color = Color.White,
            fontSize = 24.sp,
            modifier = Modifier.padding(top = 24.dp)
        )

        Box(
            modifier = Modifier
                .size(320.dp)
                .background(Color.White, shape = RoundedCornerShape(16.dp))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val distance = hypot(offset.x - startDot.x, offset.y - startDot.y)
                                if (distance <= dotRadius + 30f) {
                                    isDrawing = true
                                    pathPoints.clear()
                                    pathPoints.add(offset)
                                }
                            },
                            onDragEnd = {
                                if (isDrawing && pathPoints.isNotEmpty()) {
                                    val last = pathPoints.last()
                                    val distance = hypot(last.x - endDot.x, last.y - endDot.y)
                                    if (distance <= dotRadius + 40f) {
                                        Toast.makeText(context, "ممتاز! تم حل اللغز", Toast.LENGTH_SHORT).show()
                                    } else {
                                        pathPoints.clear()
                                    }
                                }
                                isDrawing = false
                            },
                            onDrag = { change, _ ->
                                if (isDrawing) {
                                    pathPoints.add(change.position)
                                }
                            }
                        )
                    }
            ) {
                if (pathPoints.size > 1) {
                    for (i in 0 until pathPoints.size - 1) {
                        drawLine(
                            color = Color(0xFF4CAF50),
                            start = pathPoints[i],
                            end = pathPoints[i + 1],
                            strokeWidth = 14f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                drawCircle(color = Color(0xFF2E7D32), radius = dotRadius, center = startDot)
                drawCircle(color = Color(0xFF4CAF50), radius = dotRadius - 6f, center = startDot)

                drawCircle(color = Color(0xFF2E7D32), radius = dotRadius, center = endDot)
                drawCircle(color = Color(0xFF4CAF50), radius = dotRadius - 6f, center = endDot)
            }
        }

        Button(
            onClick = { pathPoints.clear() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            Text("إعادة المحاولة", fontSize = 18.sp)
        }
    }
}
