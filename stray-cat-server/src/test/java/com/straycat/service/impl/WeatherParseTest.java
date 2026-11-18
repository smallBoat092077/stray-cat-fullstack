package com.straycat.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.straycat.vo.WeatherVO;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 和风 v1 字段映射验证：用官方示例 JSON 校验 parse() 解析结果。
 * 仅本地验证用，不依赖真实 key / 网络。
 */
class WeatherParseTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void parseRealQWeatherResponse() throws Exception {
        String currentJson = "{\n" +
                "  \"condition\": {\"text\": \"少云\", \"code\": \"102\"},\n" +
                "  \"temperature\": {\"value\": 31.71, \"unit\": \"°C\"},\n" +
                "  \"humidity\": 0.69,\n" +
                "  \"wind\": {\"direction\": {\"degree\": 226, \"compass\": \"sw\"}, \"scale\": 3}\n" +
                "}";
        String dailyJson = "{\n" +
                "  \"days\": [\n" +
                "    {\"forecastStartTime\": \"2026-11-18T22:00Z\",\n" +
                "     \"temperatureMax\": {\"value\": 32.4, \"unit\": \"°C\"},\n" +
                "     \"temperatureMin\": {\"value\": 22.1, \"unit\": \"°C\"},\n" +
                "     \"daytime\": {\"condition\": {\"text\": \"多云\", \"code\": \"101\"}}},\n" +
                "    {\"forecastStartTime\": \"2026-11-19T22:00Z\",\n" +
                "     \"temperatureMax\": {\"value\": 30.0, \"unit\": \"°C\"},\n" +
                "     \"temperatureMin\": {\"value\": 21.0, \"unit\": \"°C\"},\n" +
                "     \"daytime\": {\"condition\": {\"text\": \"晴\", \"code\": \"100\"}}}\n" +
                "  ]\n" +
                "}";

        WeatherServiceImpl service = new WeatherServiceImpl(null, null, null);
        Method parse = WeatherServiceImpl.class.getDeclaredMethod("parse", JsonNode.class, JsonNode.class);
        parse.setAccessible(true);
        WeatherVO vo = (WeatherVO) parse.invoke(service, om.readTree(currentJson), om.readTree(dailyJson));

        assertNotNull(vo.getNow(), "实况不应为空");
        assertEquals("少云", vo.getNow().getWeather());
        assertEquals(32, vo.getNow().getTemperature(), "温度应四舍五入 31.71→32");
        assertEquals(69, vo.getNow().getHumidity(), "湿度 0.69→69%");
        assertEquals("西南风", vo.getNow().getWindDirection());
        assertEquals("3级", vo.getNow().getWindPower());

        assertNotNull(vo.getFuture());
        assertEquals(2, vo.getFuture().size());
        assertEquals("2026-11-18", vo.getFuture().get(0).getDate());
        assertEquals("多云", vo.getFuture().get(0).getWeather());
        assertEquals(32, vo.getFuture().get(0).getHigh());
        assertEquals(22, vo.getFuture().get(0).getLow());

        System.out.println("=== 字段映射验证通过 ===");
        System.out.println("实况: " + vo.getNow().getWeather() + " " + vo.getNow().getTemperature()
                + "° 湿度" + vo.getNow().getHumidity() + "% " + vo.getNow().getWindDirection()
                + " " + vo.getNow().getWindPower());
        vo.getFuture().forEach(d -> System.out.println("预报: " + d.getDate() + " " + d.getWeather()
                + " " + d.getLow() + "~" + d.getHigh() + "°"));
    }
}
