package com.example.mobilka_course

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import org.json.JSONArray
import org.json.JSONObject

class PrometheusClient(context: Context) {
    private val queue = Volley.newRequestQueue(context)
    private val baseUrl = "http://opg-team.ru:9090"

    fun queryMetric(
        metricName: String,
        startTime: String = (System.currentTimeMillis() / 1000 - 3600).toString(),
        endTime: String = (System.currentTimeMillis() / 1000).toString(),
        step: String = "60s",
        onSuccess: (List<DataPoint>) -> Unit,
        onError: (String) -> Unit = { error -> Log.e("Prometheus", "Error: $error") }
    ) {
        val url = "$baseUrl/api/v1/query_range?" +
                "query=$metricName&" +
                "start=$startTime&" +
                "end=$endTime&" +
                "step=$step"

        Log.d("Prometheus", "Запрос к URL: $url")

        val request = StringRequest(
            Request.Method.GET, url,
            { response ->
                Log.d("Prometheus", "Получен ответ: ${response.length} символов")
                try {
                    val dataPoints = parsePrometheusResponse(response)
                    onSuccess(dataPoints)
                } catch (e: Exception) {
                    onError("Ошибка парсинга: ${e.message}")
                }
            },
            { error ->
                val errorMsg = "Ошибка сети: ${error.message}"
                Log.e("Prometheus", errorMsg)
                onError(errorMsg)
            }
        )

        queue.add(request)
    }

    private fun parsePrometheusResponse(response: String): List<DataPoint> {
        val dataPoints = mutableListOf<DataPoint>()

        try {
            val jsonObject = JSONObject(response)
            if (jsonObject.getString("status") != "success") {
                throw Exception("Статус ответа не success")
            }

            val data = jsonObject.getJSONObject("data")
            val resultArray = data.getJSONArray("result")

            if (resultArray.length() == 0) {
                return dataPoints
            }

            val firstResult = resultArray.getJSONObject(0)
            val values = firstResult.getJSONArray("values")

            for (i in 0 until values.length()) {
                val point = values.getJSONArray(i)
                val timestamp = point.getDouble(0).toFloat()
                val value = point.getString(1).toFloatOrNull() ?: 0f
                dataPoints.add(DataPoint(timestamp, value))
            }

            dataPoints.sortBy { it.timestamp }

        } catch (e: Exception) {
            Log.e("Prometheus", "Ошибка парсинга JSON: ${e.message}")
            throw e
        }

        return dataPoints
    }

    // Метод для получения списка доступных метрик
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

                    onSuccess(metrics.sorted())
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
}