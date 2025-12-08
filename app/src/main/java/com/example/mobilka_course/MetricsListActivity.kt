package com.example.mobilka_course

import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity

class MetricsListActivity : ComponentActivity() {

    private lateinit var listView: ListView
    private lateinit var progressBar: ProgressBar
    private lateinit var textStatus: TextView

    private lateinit var prometheusClient: PrometheusClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_metrics_list)

        listView = findViewById(R.id.listView)
        progressBar = findViewById(R.id.progressBar)
        textStatus = findViewById(R.id.textStatus)

        prometheusClient = PrometheusClient(this)

        setupUI()
        loadMetricsList()
    }

    private fun setupUI() {
        // Кнопка "Назад"
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
        }

        listView.setOnItemClickListener { parent, view, position, id ->
            val selectedMetric = parent.getItemAtPosition(position) as String
            Toast.makeText(this, "Выбрана метрика: $selectedMetric", Toast.LENGTH_SHORT).show()
        }

        // Поиск
        val searchView = findViewById<SearchView>(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                // Фильтрация будет добавлена после загрузки данных
                return false
            }
        })
    }

    private fun loadMetricsList() {
        progressBar.visibility = android.view.View.VISIBLE
        textStatus.text = "Загрузка списка метрик..."

        prometheusClient.getAvailableMetrics(
            onSuccess = { metrics ->
                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE

                    if (metrics.isEmpty()) {
                        textStatus.text = "Не удалось загрузить метрики"
                        return@runOnUiThread
                    }

                    textStatus.text = "Найдено метрик: ${metrics.size}"

                    val adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_list_item_1,
                        metrics
                    )
                    listView.adapter = adapter

                    // Настройка фильтра для поиска
                    val searchView = findViewById<SearchView>(R.id.searchView)
                    searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                        override fun onQueryTextSubmit(query: String?): Boolean {
                            return false
                        }

                        override fun onQueryTextChange(newText: String?): Boolean {
                            adapter.filter.filter(newText)
                            return true
                        }
                    })
                }
            },
            onError = { error ->
                runOnUiThread {
                    progressBar.visibility = android.view.View.GONE
                    textStatus.text = "Ошибка: $error"
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}