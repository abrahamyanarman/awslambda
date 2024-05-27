package com.task08;

import am.awslambda.task08.OpenMeteoAPILightweightSDK;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.lambda.LambdaLayer;
import com.syndicate.deployment.annotations.lambda.LambdaUrlConfig;
import com.syndicate.deployment.model.ArtifactExtension;
import com.syndicate.deployment.model.DeploymentRuntime;
import com.syndicate.deployment.model.RetentionSetting;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@LambdaHandler(lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	layers = {"open-meteo-api-lightweight-sdk"}
)
@LambdaLayer(
		layerName = "open-meteo-api-lightweight-sdk",
		runtime = DeploymentRuntime.JAVA11,
		artifactExtension = ArtifactExtension.ZIP,
		libraries = {"libraries/openMeteoAPILightweightSDK-1.0-SNAPSHOT.jar"}
)
@LambdaUrlConfig
public class ApiHandler implements RequestHandler<Object, Map<String, Object>> {
	private static final Logger logger = Logger.getLogger(ApiHandler.class.getName());

	public Map<String, Object> handleRequest(Object request, Context context) {
		logger.info("Request: " + request);

		var latitude = 50.4375;
		var longitude = 30.5;
		var api = new OpenMeteoAPILightweightSDK(latitude, longitude);
		var response = new HashMap<String, Object>();
		try {
			var weatherData = api.getWeatherForecast();
			response.put("statusCode", 200);
			response.put("body", weatherData.toString());
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error: " + e.getMessage());
			response.put("statusCode", 500);
			response.put("body", "Error: " + e.getMessage());
		}

		return response;
	}
}
