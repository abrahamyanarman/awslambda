package com.task09;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.TracingMode;
import com.task09.sdk.OpenMeteoAPILightweightSDK;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@LambdaHandler(lambdaName = "processor",
	roleName = "processor-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	tracingMode = TracingMode.Active
)
@LambdaUrlConfig
public class Processor implements RequestHandler<Object, Map<String, Object>> {
	private static final Logger logger = Logger.getLogger(Processor.class.getName());
	private static final String TABLE_NAME = "cmtr-a7a5b08f-Weather-test";
	private final static double LATITUDE = 50.4375;
	private final static double LONGITUDE = 30.5;
	private final OpenMeteoAPILightweightSDK openMeteoAPILightweightSDK = new OpenMeteoAPILightweightSDK(LATITUDE, LONGITUDE);
	private final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.standard().build();
	private final DynamoDB dynamoDB = new DynamoDB(client);
	public Map<String, Object> handleRequest(Object request, Context context) {
		logger.info("Request: " + request);
		var response = new HashMap<String, Object>();
		try {
			var weatherData = openMeteoAPILightweightSDK.getWeatherForecast();
			var formattedWeatherData = formatResponse(weatherData);
			Table table = dynamoDB.getTable(TABLE_NAME);
			Item item = new Item().withPrimaryKey("id", UUID.randomUUID().toString())
							.withMap("forecast", formatResponse(weatherData));
			table.putItem(item);

			response.put("statusCode", 200);
			response.put("body", weatherData.toString());
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error: " + e.getMessage());
			response.put("statusCode", 500);
			response.put("body", "Error: " + e.getMessage());
		}

		logger.info("Response: " + response);
		return response;
	}
	private Map<String, Object> formatResponse(JSONObject weatherData) {
		Map<String, Object> forecast = new HashMap<>();

		forecast.put("latitude", weatherData.getDouble("latitude"));
		forecast.put("longitude", weatherData.getDouble("longitude"));
		forecast.put("generationtime_ms", weatherData.getDouble("generationtime_ms"));
		forecast.put("utc_offset_seconds", weatherData.getInt("utc_offset_seconds"));
		forecast.put("timezone", weatherData.getString("timezone"));
		forecast.put("timezone_abbreviation", weatherData.getString("timezone_abbreviation"));
		forecast.put("elevation", weatherData.getDouble("elevation"));

		JSONObject hourlyUnits = weatherData.getJSONObject("hourly_units");
		Map<String, String> hourlyUnitsMap = new HashMap<>();
		hourlyUnitsMap.put("time", hourlyUnits.getString("time"));
		hourlyUnitsMap.put("temperature_2m", hourlyUnits.getString("temperature_2m"));
		forecast.put("hourly_units", hourlyUnitsMap);

		JSONObject hourly = weatherData.getJSONObject("hourly");
		Map<String, Object> hourlyMap = new HashMap<>();
		hourlyMap.put("time", hourly.getJSONArray("time").toList());
		hourlyMap.put("temperature_2m", hourly.getJSONArray("temperature_2m").toList());
		forecast.put("hourly", hourlyMap);

		return forecast;
	}
}
