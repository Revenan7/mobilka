package com.example.mobilka_course

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

class PrometheusClient(context: Context) {
    private val queue = Volley.newRequestQueue(context)
    private val baseUrl = "http://opg-team.ru:9090"

    // Типы успешных запросов для отладки
    data class QueryResult(val dataPoints: List<DataPoint>, val queryType: String)

    fun queryMetricSmart(
        metricName: String,
        startTime: String = (System.currentTimeMillis() / 1000 - 3600).toString(),
        endTime: String = (System.currentTimeMillis() / 1000).toString(),
        step: String = "60s",
        onSuccess: (List<DataPoint>, String) -> Unit,
        onError: (String) -> Unit = { error -> Log.e("Prometheus", "Error: $error") }
    ) {
        // Генерируем список различных вариантов запросов PromQL для этой метрики
        val queryVariants = generateQueryVariants(metricName)

        Log.d("Prometheus", "Пробуем ${queryVariants.size} вариантов запросов для: $metricName")

        tryQueryVariantsSmart(queryVariants, 0, startTime, endTime, step, onSuccess, onError)
    }

    private fun generateQueryVariants(metricName: String): List<Pair<String, String>> {
        val variants = mutableListOf<Pair<String, String>>()

        // Вариант 1: Прямой запрос метрики
        variants.add(Pair("direct", metricName))

        // Вариант 2: Метрика с пустыми фигурными скобками (если есть лейблы)
        variants.add(Pair("direct_with_brackets", "$metricName{}"))

        // Анализируем имя метрики для определения вероятного типа
        val lowerMetric = metricName.lowercase()

        // Вариант 3: Для метрик-счетчиков используем rate()
        if (lowerMetric.contains("_total") ||
            lowerMetric.contains("_count") ||
            lowerMetric.contains("_sum") ||
            lowerMetric.contains("requests") ||
            lowerMetric.contains("bytes") ||
            lowerMetric.contains("packets")) {

            // Разные интервалы для rate()
            listOf("1m", "5m", "10m").forEach { interval ->
                variants.add(Pair("rate_${interval}", "rate(${metricName}[$interval])"))
                variants.add(Pair("rate_${interval}_brackets", "rate(${metricName}{}[$interval])"))
            }

            // irate() для более резких изменений
            variants.add(Pair("irate", "irate(${metricName}[5m])"))
        }

        // Вариант 4: Для метрик-гистограмм (bucket)
        if (lowerMetric.contains("_bucket")) {
            // Берем квантили
            listOf(0.5, 0.75, 0.95, 0.99).forEach { quantile ->
                val quantileStr = (quantile * 100).roundToInt()
                variants.add(Pair("histogram_p${quantileStr}",
                    "histogram_quantile($quantile, sum(rate(${metricName}[5m])) by (le))"))
            }
        }

        // Вариант 5: Для метрик-измерителей (gauge) просто берем значение
        if (lowerMetric.contains("_gauge") ||
            lowerMetric.contains("memory") ||
            lowerMetric.contains("cpu") ||
            lowerMetric.contains("load") ||
            lowerMetric.contains("heap")) {

            variants.add(Pair("gauge", metricName))
            variants.add(Pair("avg_over_time", "avg_over_time(${metricName}[5m])"))
        }

        // Вариант 6: Увеличиваем интервал для rate() если стандартные не работают
        variants.add(Pair("rate_30m", "rate(${metricName}[30m])"))

        // Вариант 7: Проверяем наличие данных через increase()
        variants.add(Pair("increase", "increase(${metricName}[1h])"))

        // Вариант 8: Для метрик с префиксом "go_" (Go runtime метрики)
        if (metricName.startsWith("go_")) {
            variants.add(Pair("go_direct", metricName))
        }

        // Вариант 9: Для метрик с префиксом "process_" (метрики процесса)
        if (metricName.startsWith("process_")) {
            variants.add(Pair("process_direct", metricName))
        }

        // Вариант 10: Для метрик с префиксом "node_" (node exporter)
        if (metricName.startsWith("node_")) {
            variants.add(Pair("node_direct", metricName))
            variants.add(Pair("node_rate", "rate(${metricName}[2m])"))
        }

        Log.d("Prometheus", "Сгенерировано ${variants.size} вариантов для $metricName")
        return variants.distinctBy { it.first }
    }

    private fun tryQueryVariantsSmart(
        queryVariants: List<Pair<String, String>>,
        index: Int,
        startTime: String,
        endTime: String,
        step: String,
        onSuccess: (List<DataPoint>, String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (index >= queryVariants.size) {
            onError("Не удалось получить данные ни одним из ${queryVariants.size} способов")
            return
        }

        val (queryType, query) = queryVariants[index]
        val encodedQuery = try {
            URLEncoder.encode(query, "UTF-8")
        } catch (e: Exception) {
            query
        }

        val url = "$baseUrl/api/v1/query_range?query=$encodedQuery&start=$startTime&end=$endTime&step=$step"

        Log.d("Prometheus", "Попытка $index/$queryType: $query")

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                Log.d("Prometheus", "Ответ получен для $queryType (${response.length} chars)")
                try {
                    val dataPoints = parsePrometheusResponse(response)
                    if (dataPoints.isNotEmpty()) {
                        Log.d("Prometheus", "✅ Успех с $queryType! Найдено ${dataPoints.size} точек")
                        onSuccess(dataPoints, queryType)
                    } else {
                        Log.d("Prometheus", "❌ $queryType: данные пусты, пробуем следующий вариант")
                        // Рекурсивно пробуем следующий вариант
                        tryQueryVariantsSmart(queryVariants, index + 1, startTime, endTime, step, onSuccess, onError)
                    }
                } catch (e: Exception) {
                    Log.e("Prometheus", "Ошибка парсинга для $queryType: ${e.message}")
                    tryQueryVariantsSmart(queryVariants, index + 1, startTime, endTime, step, onSuccess, onError)
                }
            },
            { error ->
                Log.w("Prometheus", "Ошибка сети для $queryType: ${error.message}")
                tryQueryVariantsSmart(queryVariants, index + 1, startTime, endTime, step, onSuccess, onError)
            }
        )

        queue.add(request)
    }

    fun queryMetric(
        metricName: String,
        startTime: String = (System.currentTimeMillis() / 1000 - 3600).toString(),
        endTime: String = (System.currentTimeMillis() / 1000).toString(),
        step: String = "60s",
        onSuccess: (List<DataPoint>) -> Unit,
        onError: (String) -> Unit = { error -> Log.e("Prometheus", "Error: $error") }
    ) {
        // Старый метод для обратной совместимости
        queryMetricSmart(metricName, startTime, endTime, step,
            { dataPoints, _ -> onSuccess(dataPoints) },
            onError)
    }

    private fun parsePrometheusResponse(response: String): List<DataPoint> {
        val dataPoints = mutableListOf<DataPoint>()

        try {
            val jsonObject = JSONObject(response)
            if (jsonObject.getString("status") != "success") {
                Log.w("Prometheus", "Статус ответа не success")
                return dataPoints
            }

            val data = jsonObject.getJSONObject("data")
            val resultType = data.optString("resultType", "")
            val resultArray = data.getJSONArray("result")

            if (resultArray.length() == 0) {
                Log.w("Prometheus", "Пустой result массив")
                return dataPoints
            }

            // Собираем данные из всех результатов
            for (j in 0 until resultArray.length()) {
                val result = resultArray.getJSONObject(j)
                val metric = result.optJSONObject("metric")
                val values = result.optJSONArray("values")

                if (values != null && values.length() > 0) {
                    for (i in 0 until values.length()) {
                        val point = values.getJSONArray(i)
                        val timestamp = point.getDouble(0).toFloat()
                        val valueStr = point.getString(1)

                        // Пропускаем некорректные значения
                        if (valueStr == "NaN" || valueStr == "+Inf" || valueStr == "-Inf") {
                            continue
                        }

                        val value = try {
                            valueStr.toFloat()
                        } catch (e: NumberFormatException) {
                            try {
                                valueStr.toDouble().toFloat()
                            } catch (e2: NumberFormatException) {
                                Log.w("Prometheus", "Невозможно преобразовать значение: $valueStr")
                                0f
                            }
                        }

                        dataPoints.add(DataPoint(timestamp, value))
                    }
                } else {
                    // Проверяем instant запросы (значение вместо values)
                    val valueArray = result.optJSONArray("value")
                    if (valueArray != null && valueArray.length() == 2) {
                        val timestamp = valueArray.getDouble(0).toFloat()
                        val valueStr = valueArray.getString(1)

                        if (valueStr != "NaN" && valueStr != "+Inf" && valueStr != "-Inf") {
                            val value = try {
                                valueStr.toFloat()
                            } catch (e: NumberFormatException) {
                                valueStr.toDouble().toFloat()
                            }
                            dataPoints.add(DataPoint(timestamp, value))
                        }
                    }
                }
            }

            dataPoints.sortBy { it.timestamp }

            // Логируем успешный парсинг
            if (dataPoints.isNotEmpty()) {
                val firstTime = dataPoints.first().timestamp
                val lastTime = dataPoints.last().timestamp
                val avgValue = dataPoints.map { it.value }.average()
                Log.d("Prometheus", "Успешно распаршено ${dataPoints.size} точек, время: $firstTime - $lastTime, среднее: $avgValue")
            }

        } catch (e: Exception) {
            Log.e("Prometheus", "Ошибка парсинга JSON: ${e.message}")
        }

        return dataPoints
    }

    fun getAvailableMetrics(
        onSuccess: (List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        val url = "$baseUrl/api/v1/label/__name__/values"

        Log.d("Prometheus", "Запрос метрик: $url")

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                Log.d("Prometheus", "Ответ метрик: ${response.length} символов")
                try {
                    val jsonObject = JSONObject(response)
                    if (jsonObject.getString("status") != "success") {
                        onError("Статус ответа не success")
                        return@StringRequest
                    }

                    val dataArray = jsonObject.getJSONArray("data")
                    val metrics = mutableListOf<String>()

                    for (i in 0 until dataArray.length()) {
                        metrics.add(dataArray.getString(i))
                    }

                    // Фильтруем и сортируем метрики
                    val filteredMetrics = metrics
                        .filter {
                            it.isNotBlank() &&
                                    !it.startsWith("ALERTS") &&
                                    !it.startsWith("__") &&
                                    !it.contains("{") // Убираем метрики с лейблами в названии
                        }
                        .sorted()

                    onSuccess(filteredMetrics)
                } catch (e: Exception) {
                    Log.e("Prometheus", "Ошибка парсинга списка метрик: ${e.message}")
                    onError("Ошибка парсинга: ${e.message}")
                }
            },
            { error ->
                val errorMsg = "Ошибка сети при получении метрик: ${error.message}"
                Log.e("Prometheus", errorMsg)
                onError(errorMsg)
            }
        )

        queue.add(request)
    }

    // Функция для быстрой проверки метрики
    fun quickCheckMetric(metricName: String, callback: (Boolean, String, Int) -> Unit) {
        val now = System.currentTimeMillis() / 1000
        val startTime = now - 1800 // 30 минут назад

        queryMetricSmart(metricName, startTime.toString(), now.toString(), "30s",
            { dataPoints, queryType ->
                callback(true, queryType, dataPoints.size)
            },
            { error ->
                callback(false, error, 0)
            }
        )
    }
}