package com.example.mobilka_course

import android.graphics.Color
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class ChartActivity : ComponentActivity() {

    private lateinit var lineChart: LineChart
    private lateinit var metricSpinner: Spinner
    private lateinit var btnRefresh: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var textStatus: TextView

    private lateinit var prometheusClient: PrometheusClient

    // Список популярных метрик для старта
    private val defaultMetrics = listOf(
        "up",
        "process_cpu_seconds_total",
        "process_resident_memory_bytes",
        "go_goroutines",
        "go_memstats_alloc_bytes"
    )

    private var currentMetric = "up"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chart)

        // Инициализация UI элементов
        lineChart = findViewById(R.id.lineChart)
        metricSpinner = findViewById(R.id.metricSpinner)
        btnRefresh = findViewById(R.id.btnRefresh)
        progressBar = findViewById(R.id.progressBar)
        textStatus = findViewById(R.id.textStatus)

        // Инициализация клиента Prometheus
        prometheusClient = PrometheusClient(this)

        // Настройка UI
        setupChart()
        setupSpinner()
        setupButtons()

        // Загрузка начальных данных
        loadMetricData(currentMetric)
    }

    private fun setupChart() {
        lineChart.setTouchEnabled(true)
        lineChart.setPinchZoom(true)
        lineChart.description.isEnabled = false
        lineChart.setDrawGridBackground(false)
        lineChart.setNoDataText("Загрузка данных...")
        lineChart.setNoDataTextColor(Color.GRAY)

        // Настройка оси X
        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)
        xAxis.labelCount = 5
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return try {
                    val date = Date((value * 1000).toLong())
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                } catch (e: Exception) {
                    value.toString()
                }
            }
        }

        // Настройка левой оси Y
        val leftAxis = lineChart.axisLeft
        leftAxis.setDrawGridLines(true)
        leftAxis.gridColor = Color.LTGRAY
        leftAxis.axisLineColor = Color.DKGRAY

        // Настройка правой оси Y
        val rightAxis = lineChart.axisRight
        rightAxis.isEnabled = false

        // Настройка легенды
        lineChart.legend.isEnabled = true
        lineChart.legend.textColor = Color.DKGRAY
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, defaultMetrics)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        metricSpinner.adapter = adapter

        metricSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                currentMetric = defaultMetrics[position]
                loadMetricData(currentMetric)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Ничего не делаем
            }
        }
    }

    private fun setupButtons() {
        btnRefresh.setOnClickListener {
            loadMetricData(currentMetric)
        }

        // Кнопка "Назад"
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }
    }

    private fun loadMetricData(metricName: String) {
        // Показать прогресс бар
        progressBar.visibility = android.view.View.VISIBLE
        textStatus.text = "Загрузка данных..."

        // Рассчитать временной диапазон (последний час)
        val now = System.currentTimeMillis() / 1000
        val oneHourAgo = now - 3600

        prometheusClient.queryMetric(
            metricName = metricName,
            startTime = oneHourAgo.toString(),
            endTime = now.toString(),
            step = "60s", // Интервал 1 минута
            onSuccess = { dataPoints ->
                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE

                    if (dataPoints.isEmpty()) {
                        textStatus.text = "Нет данных для метрики: $metricName"
                        textStatus.setTextColor(Color.RED)
                        lineChart.clear()
                        lineChart.invalidate()
                        Toast.makeText(this@ChartActivity, "Нет данных", Toast.LENGTH_SHORT).show()
                    } else {
                        textStatus.text = "Данные загружены. Точек: ${dataPoints.size}"
                        textStatus.setTextColor(Color.GREEN)
                        updateChart(dataPoints, metricName)
                    }
                }
            },
            onError = { error ->
                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE
                    textStatus.text = "Ошибка: $error"
                    textStatus.setTextColor(Color.RED)
                    Toast.makeText(this@ChartActivity, error, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun updateChart(dataPoints: List<DataPoint>, metricName: String) {
        if (dataPoints.isEmpty()) {
            lineChart.clear()
            lineChart.invalidate()
            return
        }

        val entries = ArrayList<Entry>()

        for (point in dataPoints) {
            // Преобразуем timestamp в секунды для оси X
            entries.add(Entry(point.timestamp, point.value))
        }

        val dataSet = LineDataSet(entries, metricName)
        dataSet.color = Color.BLUE
        dataSet.valueTextColor = Color.BLACK
        dataSet.lineWidth = 2f
        dataSet.setCircleColor(Color.RED)
        dataSet.circleRadius = 3f
        dataSet.setDrawCircleHole(false)
        dataSet.setDrawValues(false) // Не показывать значения на точках
        dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER // Сглаженная линия

        val lineData = LineData(dataSet)
        lineChart.data = lineData
        lineChart.invalidate() // Обновить график
        lineChart.animateX(1000) // Анимация появления
    }
}