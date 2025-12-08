package com.example.mobilka_course

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobilka_course.ui.theme.Mobilka_courseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Mobilka_courseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onNavigateToCharts = {
                            val intent = Intent(this@MainActivity, ChartActivity::class.java)
                            startActivity(intent)
                        },
                        onNavigateToMetricsList = {
                            val intent = Intent(this@MainActivity, MetricsListActivity::class.java)
                            startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    onNavigateToCharts: () -> Unit,
    onNavigateToMetricsList: () -> Unit
) {
    val context = LocalContext.current
    val isOnline = remember { NetworkUtils.isInternetAvailable(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AppHeader()
        if (!isOnline) NetworkStatusCard()
        AppDescription()
        ButtonsSection(
            onNavigateToCharts = onNavigateToCharts,
            onNavigateToMetricsList = onNavigateToMetricsList,
            enabled = isOnline
        )
        ServerInfoCard(isOnline)
    }
}

@Composable
fun AppHeader() {
    Text(
        text = "📊 Prometheus Monitor",
        fontSize = 32.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
fun AppDescription() {
    Text(
        text = "Мобильное приложение для мониторинга метрик",
        fontSize = 16.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 24.dp)
    )
}

@Composable
fun ButtonsSection(
    onNavigateToCharts: () -> Unit,
    onNavigateToMetricsList: () -> Unit,
    enabled: Boolean
) {
    Button(
        onClick = onNavigateToCharts,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        enabled = enabled
    ) {
        Text(
            text = "Показать графики метрик",
            fontSize = 18.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }

    OutlinedButton(
        onClick = onNavigateToMetricsList,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        enabled = enabled
    ) {
        Text(
            text = "Список доступных метрик",
            fontSize = 16.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
fun NetworkStatusCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Text(
            text = "⚠ Нет подключения к интернету",
            modifier = Modifier.padding(12.dp),
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
fun ServerInfoCard(isOnline: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Информация о сервере",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Prometheus: http://opg-team.ru:9090",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Статус: ${if (isOnline) "Подключено" else "Нет подключения"}",
                color = if (isOnline) Color.Green else Color.Red,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
