package com.task09;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.RetentionSetting;
import com.syndicate.deployment.model.TracingMode;
import com.task09.sdk.OpenMeteoAPILightweightSDK;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@LambdaHandler(lambdaName = "processor",
	roleName = "processor-role",
	isPublishVersion = true,
	aliasName = "${lambdas_alias_name}",
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	tracingMode = TracingMode.Active
)
@LambdaUrlConfig
public class Processor implements RequestHandler<Object, Map<String, Object>> {
	private static final Logger logger = Logger.getLogger(Processor.class.getName());
	private static final String INSERT_EVENT = "INSERT";
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

			Table table = dynamoDB.getTable(TABLE_NAME);
			Item item = new Item().withPrimaryKey("id", UUID.randomUUID().toString())
							.with("forecast", weatherData);
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
}
