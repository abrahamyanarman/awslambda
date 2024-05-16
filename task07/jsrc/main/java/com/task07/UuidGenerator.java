package com.task07;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.events.EventBridgeRuleSource;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.task07.model.UUIDData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@LambdaHandler(lambdaName = "uuid_generator",
	roleName = "uuid_generator-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED,
	timeout = 60
)
@EventBridgeRuleSource(targetRule = "uuid_trigger")
public class UuidGenerator implements RequestHandler<Object, Void> {

	private static final Logger logger = Logger.getLogger(UuidGenerator.class.getName());
	private static final ObjectMapper mapper = new ObjectMapper();
	private static final AmazonS3 s3Client = AmazonS3ClientBuilder.standard().build();
	public Void handleRequest(Object request, Context context) {
		String currentIsoTime = Instant.now().toString();
		ArrayList<String> uuids = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			uuids.add(UUID.randomUUID().toString());
		}
		UUIDData data = new UUIDData(uuids);
		String jsonData = "";
		try {
			jsonData = mapper.writeValueAsString(data);
		} catch (JsonProcessingException e) {
			logger.info("Error converting data to JSON: " + e.getMessage());
		}

		s3Client.putObject("cmtr-a7a5b08f-uuid-storage-test", currentIsoTime, jsonData);
		logger.info("Successfully uploaded data to S3: " + currentIsoTime);

		return null;
	}
}
