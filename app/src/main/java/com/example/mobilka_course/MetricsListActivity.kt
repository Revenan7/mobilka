package com.example.mobilka_course

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog

class MetricsListActivity : ComponentActivity() {

    private lateinit var listView: ExpandableListView
    private lateinit var progressBar: ProgressBar
    private lateinit var textStatus: TextView

    private lateinit var prometheusClient: PrometheusClient
    private val groupedMetrics = mutableMapOf<String, List<String>>()
    private val groupList = mutableListOf<String>()

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

        // Обработчик выбора метрики
        listView.setOnChildClickListener { parent, v, groupPosition, childPosition, id ->
            val groupName = groupList[groupPosition]
            val metricName = groupedMetrics[groupName]?.get(childPosition) ?: ""

            Toast.makeText(this, "Выбрана метрика: $metricName", Toast.LENGTH_SHORT).show()

            // Открываем ChartActivity с выбранной метрикой
            val intent = Intent(this, ChartActivity::class.java)
            intent.putExtra("selected_metric", metricName)
            startActivity(intent)
            true
        }

        // Поиск
        val searchView = findViewById<SearchView>(R.id.searchView)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterMetrics(newText)
                return true
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

                    // Группируем метрики по префиксу (часть до первого подчеркивания)
                    groupMetricsByPrefix(metrics)

                    textStatus.text = "Найдено метрик: ${metrics.size}, групп: ${groupList.size}"

                    // Создаем адаптер для ExpandableListView
                    val adapter = object : BaseExpandableListAdapter() {
                        override fun getGroupCount(): Int = groupList.size

                        override fun getChildrenCount(groupPosition: Int): Int =
                            groupedMetrics[groupList[groupPosition]]?.size ?: 0

                        override fun getGroup(groupPosition: Int): Any = groupList[groupPosition]

                        override fun getChild(groupPosition: Int, childPosition: Int): Any =
                            groupedMetrics[groupList[groupPosition]]?.get(childPosition) ?: ""

                        override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()

                        override fun getChildId(groupPosition: Int, childPosition: Int): Long =
                            (groupPosition * 1000 + childPosition).toLong()

                        override fun hasStableIds(): Boolean = true

                        override fun getGroupView(
                            groupPosition: Int,
                            isExpanded: Boolean,
                            convertView: android.view.View?,
                            parent: android.view.ViewGroup?
                        ): android.view.View {
                            val view = convertView ?: layoutInflater.inflate(
                                android.R.layout.simple_expandable_list_item_1,
                                parent,
                                false
                            )

                            val textView = view.findViewById<TextView>(android.R.id.text1)
                            val groupName = groupList[groupPosition]
                            val count = getChildrenCount(groupPosition)
                            textView.text = "$groupName ($count)"
                            textView.setPadding(50, 20, 20, 20)
                            textView.textSize = 16f

                            return view
                        }

                        override fun getChildView(
                            groupPosition: Int,
                            childPosition: Int,
                            isLastChild: Boolean,
                            convertView: android.view.View?,
                            parent: android.view.ViewGroup?
                        ): android.view.View {
                            val view = convertView ?: layoutInflater.inflate(
                                android.R.layout.simple_list_item_1,
                                parent,
                                false
                            )

                            val textView = view.findViewById<TextView>(android.R.id.text1)
                            val metricName = getChild(groupPosition, childPosition) as String
                            textView.text = "  • $metricName"
                            textView.setPadding(80, 15, 20, 15)
                            textView.textSize = 14f

                            return view
                        }

                        override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true
                    }

                    listView.setAdapter(adapter)

                    // Разворачиваем первую группу для примера
                    if (groupList.isNotEmpty()) {
                        listView.expandGroup(0)
                    }
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

    private fun groupMetricsByPrefix(metrics: List<String>) {
        groupedMetrics.clear()
        groupList.clear()

        // Создаем временную мапу для группировки
        val tempMap = mutableMapOf<String, MutableList<String>>()

        for (metric in metrics.sorted()) {
            // Берем часть до первого подчеркивания как имя группы
            val firstUnderscore = metric.indexOf('_')
            val groupName = if (firstUnderscore != -1) {
                metric.substring(0, firstUnderscore)
            } else {
                "other"
            }

            if (!tempMap.containsKey(groupName)) {
                tempMap[groupName] = mutableListOf()
            }
            tempMap[groupName]?.add(metric)
        }

        // Преобразуем в отсортированные списки
        val sortedGroups = tempMap.keys.sorted()
        for (group in sortedGroups) {
            val groupMetrics = tempMap[group]?.sorted() ?: continue
            groupedMetrics[group] = groupMetrics
            groupList.add(group)
        }
    }

    private fun filterMetrics(query: String?) {
        // Если запрос пустой, показываем все группы
        if (query.isNullOrBlank()) {
            loadMetricsList()
            return
        }

        val filteredGroups = mutableMapOf<String, List<String>>()
        val filteredGroupList = mutableListOf<String>()

        val searchQuery = query.lowercase()

        // Ищем метрики, содержащие запрос
        for ((groupName, metrics) in groupedMetrics) {
            val filteredMetrics = metrics.filter {
                it.lowercase().contains(searchQuery)
            }

            if (filteredMetrics.isNotEmpty()) {
                filteredGroups[groupName] = filteredMetrics
                filteredGroupList.add(groupName)
            }
        }

        // Обновляем адаптер
        updateAdapterWithFilteredData(filteredGroupList, filteredGroups)
    }

    private fun updateAdapterWithFilteredData(
        groups: List<String>,
        metrics: Map<String, List<String>>
    ) {
        val adapter = object : BaseExpandableListAdapter() {
            override fun getGroupCount(): Int = groups.size

            override fun getChildrenCount(groupPosition: Int): Int =
                metrics[groups[groupPosition]]?.size ?: 0

            override fun getGroup(groupPosition: Int): Any = groups[groupPosition]

            override fun getChild(groupPosition: Int, childPosition: Int): Any =
                metrics[groups[groupPosition]]?.get(childPosition) ?: ""

            override fun getGroupId(groupPosition: Int): Long = groupPosition.toLong()

            override fun getChildId(groupPosition: Int, childPosition: Int): Long =
                (groupPosition * 1000 + childPosition).toLong()

            override fun hasStableIds(): Boolean = true

            override fun getGroupView(
                groupPosition: Int,
                isExpanded: Boolean,
                convertView: android.view.View?,
                parent: android.view.ViewGroup?
            ): android.view.View {
                val view = convertView ?: layoutInflater.inflate(
                    android.R.layout.simple_expandable_list_item_1,
                    parent,
                    false
                )

                val textView = view.findViewById<TextView>(android.R.id.text1)
                val groupName = groups[groupPosition]
                val count = getChildrenCount(groupPosition)
                textView.text = "$groupName ($count)"
                textView.setPadding(50, 20, 20, 20)
                textView.textSize = 16f

                return view
            }

            override fun getChildView(
                groupPosition: Int,
                childPosition: Int,
                isLastChild: Boolean,
                convertView: android.view.View?,
                parent: android.view.ViewGroup?
            ): android.view.View {
                val view = convertView ?: layoutInflater.inflate(
                    android.R.layout.simple_list_item_1,
                    parent,
                    false
                )

                val textView = view.findViewById<TextView>(android.R.id.text1)
                val metricName = getChild(groupPosition, childPosition) as String
                textView.text = "  • $metricName"
                textView.setPadding(80, 15, 20, 15)
                textView.textSize = 14f

                return view
            }

            override fun isChildSelectable(groupPosition: Int, childPosition: Int): Boolean = true
        }

        listView.setAdapter(adapter)

        // Разворачиваем все группы при фильтрации
        for (i in 0 until adapter.groupCount) {
            listView.expandGroup(i)
        }

        textStatus.text = "Найдено: ${metrics.values.sumOf { it.size }} метрик в ${groups.size} группах"
    }
}