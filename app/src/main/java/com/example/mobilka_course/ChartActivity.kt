package com.example.mobilka_course

import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class ChartActivity : ComponentActivity() {

    private lateinit var lineChart: LineChart
    private lateinit var metricSpinner: Spinner
    private lateinit var btnRefresh: Button
    private lateinit var btnBack: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var textStatus: TextView
    private lateinit var textMetricInfo: TextView
    private lateinit var textQueryType: TextView

    private lateinit var prometheusClient: PrometheusClient
    private var allMetrics = mutableListOf<String>()

    private val defaultMetrics = listOf(
        "up",
        "process_cpu_seconds_total",
        "process_resident_memory_bytes",
        "go_goroutines",
        "go_memstats_alloc_bytes",
        "node_cpu_seconds_total",
        "node_memory_MemFree_bytes",
        "http_requests_total"
    )

    private var currentMetric = "up"
    private var currentQueryType = "direct"
    private var timeRange = 3600 // 1 час в секундах
    private var step = "60s"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chart)

        // Получаем метрику из Intent
        intent.getStringExtra("selected_metric")?.let {
            currentMetric = it
        }

        initViews()
        setupChart()
        loadAllMetricsAndSetupSpinner()
        setupButtons()
    }

    private fun initViews() {
        lineChart = findViewById(R.id.lineChart)
        metricSpinner = findViewById(R.id.metricSpinner)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnBack = findViewById(R.id.btnBack)
        progressBar = findViewById(R.id.progressBar)
        textStatus = findViewById(R.id.textStatus)
        textMetricInfo = findViewById(R.id.textMetricInfo)
        textQueryType = findViewById(R.id.textQueryType)

        prometheusClient = PrometheusClient(this)
    }

    private fun loadAllMetricsAndSetupSpinner() {
        progressBar.visibility = android.view.View.VISIBLE
        textStatus.text = "Загрузка списка метрик..."
        textQueryType.text = "Тип запроса: -"

        prometheusClient.getAvailableMetrics(
            onSuccess = { metrics ->
                runOnUiThread {
                    allMetrics.clear()
                    allMetrics.addAll(metrics)

                    // Создаем объединенный список: сначала популярные, потом все остальные
                    val combinedMetrics = mutableListOf<String>()
                    combinedMetrics.addAll(defaultMetrics)
                    combinedMetrics.addAll(metrics.filter { !defaultMetrics.contains(it) })

                    setupSpinner(combinedMetrics)
                    loadMetricDataSmart(currentMetric)
                }
            },
            onError = { error ->
                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE
                    textStatus.text = "Ошибка загрузки метрик: $error"
                    setupSpinner(defaultMetrics)
                    loadMetricDataSmart(currentMetric)
                }
            }
        )
    }

    private fun setupSpinner(metrics: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, metrics)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        metricSpinner.adapter = adapter

        // Устанавливаем текущую метрику в спиннер
        val position = metrics.indexOf(currentMetric)
        if (position >= 0) {
            metricSpinner.setSelection(position)
        } else if (metrics.isNotEmpty()) {
            currentMetric = metrics[0]
        }

        metricSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedMetric = parent?.getItemAtPosition(position) as? String
                selectedMetric?.let {
                    if (it != currentMetric) {
                        currentMetric = it
                        currentQueryType = "direct" // Сбрасываем тип запроса при смене метрики
                        loadMetricDataSmart(it)
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupChart() {
        // Очищаем график
        lineChart.clear()

        // Основные настройки
        lineChart.setTouchEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.setDragEnabled(true)
        lineChart.setScaleEnabled(true)
        lineChart.description.isEnabled = false
        lineChart.setDrawGridBackground(false)
        lineChart.setNoDataText("Загрузка данных...")
        lineChart.setNoDataTextColor(Color.GRAY)

        // Устанавливаем отступы (ВАЖНО для правильного отображения)
        lineChart.setExtraOffsets(20f, 20f, 20f, 40f)

        // Настройка оси X
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(true)
        xAxis.gridColor = Color.parseColor("#33000000")
        xAxis.labelCount = 6
        xAxis.textColor = Color.DKGRAY
        xAxis.textSize = 10f

        // Форматирование времени на оси X (слева направо)
        xAxis.valueFormatter = object : ValueFormatter() {
            private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

            override fun getFormattedValue(value: Float): String {
                return try {
                    val date = Date((value * 1000).toLong())
                    dateFormat.format(date)
                } catch (e: Exception) {
                    ""
                }
            }
        }

        // Настройка чтобы график шел слева направо
        xAxis.setAvoidFirstLastClipping(true)
        xAxis.setCenterAxisLabels(false)

        // Настройка левой оси Y
        val leftAxis = lineChart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.parseColor("#33000000")
        leftAxis.axisLineColor = Color.DKGRAY
        leftAxis.textColor = Color.DKGRAY
        leftAxis.textSize = 10f
        leftAxis.setDrawZeroLine(false)
        leftAxis.setDrawTopYLabelEntry(false)

        // Форматирование чисел на оси Y (без научной нотации)
        leftAxis.valueFormatter = object : ValueFormatter() {
            private val decimalFormat = DecimalFormat("#,###.##")

            override fun getFormattedValue(value: Float): String {
                // Обрабатываем большие числа - показываем в удобном формате
                return when {
                    value >= 1_000_000_000 -> String.format("%.1fG", value / 1_000_000_000)
                    value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000)
                    value >= 1_000 -> String.format("%.1fK", value / 1_000)
                    value >= 1 -> decimalFormat.format(value.toDouble())
                    value > 0.001 -> String.format("%.4f", value)
                    else -> decimalFormat.format(value.toDouble())
                }
            }
        }

        // Настройка правой оси Y
        val rightAxis = lineChart.axisRight
        rightAxis.isEnabled = false

        // Настройка легенды
        val legend = lineChart.legend
        legend.isEnabled = true
        legend.textColor = Color.DKGRAY
        legend.textSize = 12f
        legend.verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
        legend.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
        legend.orientation = Legend.LegendOrientation.HORIZONTAL
        legend.setDrawInside(false)
        legend.yOffset = 15f

        // Отключаем подписи значений
        lineChart.setDrawMarkers(false)
    }

    private fun setupButtons() {
        btnBack.setOnClickListener {
            finish()
        }

        btnRefresh.setOnClickListener {
            loadMetricDataSmart(currentMetric)
        }
    }

    private fun loadMetricDataSmart(metricName: String) {
        progressBar.visibility = android.view.View.VISIBLE
        textStatus.text = "Умная загрузка данных для '$metricName'..."
        textMetricInfo.text = "Метрика: $metricName\nПериод: ${formatTimeRange()}"
        textQueryType.text = "Тип запроса: определение..."

        val now = System.currentTimeMillis() / 1000
        val startTime = now - timeRange

        prometheusClient.queryMetricSmart(
            metricName = metricName,
            startTime = startTime.toString(),
            endTime = now.toString(),
            step = step,
            onSuccess = { dataPoints, queryType ->
                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE
                    currentQueryType = queryType

                    if (dataPoints.isEmpty()) {
                        textStatus.text = "Не удалось получить данные для метрики: $metricName"
                        textStatus.setTextColor(Color.RED)
                        textQueryType.text = "Тип запроса: не найдено"
                        lineChart.clear()
                        lineChart.invalidate()
                        lineChart.setNoDataText("Нет данных (испробованы все типы запросов)")
                        Toast.makeText(
                            this@ChartActivity,
                            "Не удалось получить данные ни одним из способов",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        textStatus.text = "Загружено точек: ${dataPoints.size}"
                        textStatus.setTextColor(Color.parseColor("#388E3C"))
                        textQueryType.text = "Тип запроса: $queryType"
                        updateChart(dataPoints, metricName, queryType)
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE
                    textStatus.text = "Ошибка: ${error.take(100)}..."
                    textStatus.setTextColor(Color.RED)
                    textQueryType.text = "Тип запроса: ошибка"
                    Toast.makeText(this@ChartActivity, error, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun formatTimeRange(): String {
        return when (timeRange) {
            900 -> "15 минут"
            3600 -> "1 час"
            10800 -> "3 часа"
            21600 -> "6 часов"
            43200 -> "12 часов"
            86400 -> "1 день"
            else -> "${timeRange / 60} минут"
        }
    }

    private fun updateChart(dataPoints: List<DataPoint>, metricName: String, queryType: String) {
        if (dataPoints.isEmpty()) {
            return
        }

        // Сортируем точки по времени (от старых к новым)
        val sortedPoints = dataPoints.sortedBy { it.timestamp }

        // Создаем Entry для каждой точки
        val entries = ArrayList<Entry>()
        for (point in sortedPoints) {
            entries.add(Entry(point.timestamp, point.value))
        }

        if (entries.isEmpty()) {
            return
        }

        // Определяем цвет графика в зависимости от типа запроса
        val lineColor = when {
            queryType.contains("rate") -> Color.parseColor("#4CAF50") // Зеленый для rate
            queryType.contains("histogram") -> Color.parseColor("#FF9800") // Оранжевый для гистограмм
            queryType.contains("irate") -> Color.parseColor("#9C27B0") // Фиолетовый для irate
            else -> Color.parseColor("#2196F3") // Синий по умолчанию
        }

        // Создаем набор данных
        val dataSet = LineDataSet(entries, "$metricName [$queryType]")
        dataSet.color = lineColor
        dataSet.valueTextColor = Color.BLACK
        dataSet.lineWidth = 2.5f
        dataSet.setCircleColor(lineColor)
        dataSet.circleRadius = 3f
        dataSet.setDrawCircleHole(true)
        dataSet.circleHoleRadius = 1.5f
        dataSet.setDrawValues(false)
        dataSet.mode = LineDataSet.Mode.LINEAR
        dataSet.setDrawFilled(true)
        dataSet.fillColor = lineColor
        dataSet.fillAlpha = 20
        dataSet.setDrawCircles(dataPoints.size <= 50) // Рисуем кружки только если точек не слишком много

        // Создаем данные для графика
        val lineData = LineData(dataSet)
        lineData.setValueTextSize(9f)
        lineData.setValueTextColor(Color.BLACK)

        // Устанавливаем данные и обновляем график
        lineChart.data = lineData

        // Настраиваем оси в зависимости от данных
        if (entries.isNotEmpty()) {
            val minX = entries.minByOrNull { it.x }?.x ?: 0f
            val maxX = entries.maxByOrNull { it.x }?.x ?: 0f

            if (maxX > minX) {
                val paddingX = (maxX - minX) * 0.05f
                lineChart.xAxis.axisMinimum = minX - paddingX
                lineChart.xAxis.axisMaximum = maxX + paddingX
            }

            // Настройка оси Y с учетом значений
            val minY = entries.minByOrNull { it.y }?.y ?: 0f
            val maxY = entries.maxByOrNull { it.y }?.y ?: 0f

            if (maxY > minY) {
                val paddingY = (maxY - minY) * 0.1f
                lineChart.axisLeft.axisMinimum = minY - paddingY
                lineChart.axisLeft.axisMaximum = maxY + paddingY

                // Автоматически устанавливаем формат чисел для оси Y
                val range = maxY - minY
                lineChart.axisLeft.labelCount = if (range > 1000) 6 else 5
            } else if (maxY == minY && maxY != 0f) {
                // Если все значения одинаковые (но не нулевые)
                lineChart.axisLeft.axisMinimum = maxY * 0.5f
                lineChart.axisLeft.axisMaximum = maxY * 1.5f
            }
        }

        // Принудительно обновляем график
        lineChart.notifyDataSetChanged()
        lineChart.invalidate()

        // Анимация
        lineChart.animateX(800)

        // Обновляем описание с типом запроса
        lineChart.description.text = "Тип запроса: $queryType | Точки: ${dataPoints.size}"
        lineChart.description.textSize = 9f
        lineChart.description.textColor = Color.DKGRAY

        // Логируем успешное обновление
            //Log.d("ChartActivity", "График обновлен: $metricName, тип: $queryType, точек: ${dataPoints.size}")
    }
}