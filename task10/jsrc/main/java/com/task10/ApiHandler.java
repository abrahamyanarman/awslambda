package com.task10;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.GetItemRequest;
import com.amazonaws.services.dynamodbv2.model.GetItemResult;
import com.amazonaws.services.dynamodbv2.model.ScanRequest;
import com.amazonaws.services.dynamodbv2.model.ScanResult;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.model.RetentionSetting;
import com.task10.model.Reservation;
import com.task10.model.Tables;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminInitiateAuthResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUserPoolClientsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUserPoolClientsResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUserPoolsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUserPoolsResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserPoolClientDescription;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserPoolDescriptionType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@LambdaHandler(lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	private static final Logger logger = Logger.getLogger(ApiHandler.class.getName());
	private static final String USER_POOL_ID = "cmtr-a7a5b08f-simple-booking-userpool-test";
	private static final String CLIENT_NAME = "cmtr-a7a5b08f-task10-client";

	private static final String SIGN_UP_PATH = "/signup";
	private static final String SIGN_IN_PATH = "/signin";
	private static final String TABLES_PATH = "/tables";
	private static final String START_WITH_TABLES_PATH = "/tables/";
	private static final String RESERVATIONS_PATH = "/reservations";
	private static final String POST = "POST";
	private static final String GET = "GET";

	private static final int SUCCESS_STATUS_CODE = 200;

	private final AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.defaultClient();
	private final DynamoDB dynamoDB = new DynamoDB(dynamoDBClient);
	private final Table tablesTable = dynamoDB.getTable("cmtr-a7a5b08f-Tables-test");
	private final Table reservationsTable = dynamoDB.getTable("cmtr-a7a5b08f-Reservations-test");
	private final CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.create();
	private final ObjectMapper objectMapper = new ObjectMapper();

	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
		String httpMethod = event.getHttpMethod();
		String path = event.getPath();
		String body = event.getBody();
		APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
		response.setHeaders(Collections.singletonMap("Content-Type", "application/json"));

		try {
			if (SIGN_UP_PATH.equals(path) && POST.equalsIgnoreCase(httpMethod)) {
				logger.info("Start handling Signup request with body: " + body);
				return handleSignup(body);
			} else if (SIGN_IN_PATH.equals(path) && POST.equalsIgnoreCase(httpMethod)) {
				return handleSignin(body);
			} else if (TABLES_PATH.equals(path) && GET.equalsIgnoreCase(httpMethod)) {
				return handleGetTables();
			} else if (TABLES_PATH.equals(path) && POST.equalsIgnoreCase(httpMethod)) {
				return handleCreateTable(body);
			} else if (path.startsWith(START_WITH_TABLES_PATH) && GET.equalsIgnoreCase(httpMethod)) {
				int tableId = Integer.parseInt(path.split("/")[2]);
				return handleGetTable(tableId);
			} else if (RESERVATIONS_PATH.equals(path) && POST.equalsIgnoreCase(httpMethod)) {
				return handleCreateReservation(body);
			} else if (RESERVATIONS_PATH.equals(path) && GET.equalsIgnoreCase(httpMethod)) {
				return handleGetReservations();
			} else {
				response.setStatusCode(400);
				response.setBody("{\"message\":\"Bad Request\"}");
			}
		} catch (UserNotFoundException unfe) {
			logger.warning("User Not Found!");
			response.setStatusCode(400);
			response.setBody(String.format("{\"message\":\"%s\"}", unfe.getMessage()));
		}catch (Exception e) {
			logger.log(Level.SEVERE, "Internal Server Error message: " + e.getMessage() + "stackTrace: " + Arrays.toString(e.getStackTrace()));
			response.setStatusCode(500);
			response.setBody("{\"message\":\"Internal Server Error\"}");
		}

		return response;

	}

	private APIGatewayProxyResponseEvent handleSignup(String body) throws Exception {
		Map<String, String> requestBody = objectMapper.readValue(body, HashMap.class);
		logger.info("Signup request body: " + requestBody);
		if (!isValidSignupRequest(requestBody)) {
			logger.log(Level.WARNING, "Request body not valid for Signup");
			return createErrorResponse(400, "Invalid request");
		}

		ListUserPoolsRequest listUserPoolsRequest = ListUserPoolsRequest.builder().build();
		ListUserPoolsResponse listUserPoolsResponse = cognitoClient.listUserPools(listUserPoolsRequest);
		List<UserPoolDescriptionType> userPools = listUserPoolsResponse.userPools();

		logger.info("SignUp, userPools: " + userPools);
		String userPoolId =  userPools.stream()
				.filter(userPool -> userPool.name().equals(USER_POOL_ID))
				.map(UserPoolDescriptionType::id)
				.findFirst().orElseThrow();

		AdminCreateUserRequest createUserRequest = AdminCreateUserRequest.builder()
				.userPoolId(userPoolId)
				.username(requestBody.get("email"))
				.userAttributes(
						AttributeType.builder().name("email").value(requestBody.get("email")).build(),
						AttributeType.builder().name("email_verified").value("true").build(),
						AttributeType.builder().name("given_name").value(requestBody.get("firstName")).build(),
						AttributeType.builder().name("family_name").value(requestBody.get("lastName")).build()
				)
				.temporaryPassword(requestBody.get("password"))
				.messageAction("SUPPRESS")
				.build();
		cognitoClient.adminCreateUser(createUserRequest);
		AdminSetUserPasswordRequest adminSetUserPasswordRequest = AdminSetUserPasswordRequest.builder()
						.userPoolId(userPoolId)
								.username(requestBody.get("email"))
										.password(requestBody.get("password"))
												.permanent(true)
														.build();
		logger.info("AdminSetUserPasswordRequest: " + adminSetUserPasswordRequest);
		AdminSetUserPasswordResponse adminSetUserPasswordResponse = cognitoClient.adminSetUserPassword(adminSetUserPasswordRequest);
		logger.info("AdminSetUserPasswordResponse: " + adminSetUserPasswordResponse);

		logger.info("Calling adminCreateUser: " + createUserRequest);

		return createSuccessResponse("{\"message\":\"Sign-up successful\"}");
	}

	private boolean isValidSignupRequest(Map<String, String> requestBody) {
		return requestBody.containsKey("firstName") &&
				requestBody.containsKey("lastName") &&
				isValidEmail(requestBody.get("email")) &&
				isValidPassword(requestBody.get("password"));
	}

	private boolean isValidEmail(String email) {
		String emailRegex = "^[^@]+@[^@]+\\.[^@]+$";
		Pattern pattern = Pattern.compile(emailRegex);
		Matcher matcher = pattern.matcher(email);
		return matcher.matches();
	}

	private boolean isValidPassword(String password) {
		String passwordRegex = "^[a-zA-Z][\\w$%^*-_.]*$";
		Pattern pattern = Pattern.compile(passwordRegex);
		Matcher matcher = pattern.matcher(password);
		return matcher.matches();
	}

	private APIGatewayProxyResponseEvent handleSignin(String body) throws Exception {
		Map<String, String> requestBody = objectMapper.readValue(body, HashMap.class);
		logger.info("Signin request body: " + requestBody);
		if (!isValidSigninRequest(requestBody)) {
			return createErrorResponse(400, "Invalid request");
		}

		ListUserPoolsRequest listUserPoolsRequest = ListUserPoolsRequest.builder().build();
		ListUserPoolsResponse listUserPoolsResponse = cognitoClient.listUserPools(listUserPoolsRequest);
		List<UserPoolDescriptionType> userPools = listUserPoolsResponse.userPools();

		String userPoolId =  userPools.stream()
				.filter(userPool -> userPool.name().equals(USER_POOL_ID))
				.map(UserPoolDescriptionType::id)
				.findFirst().orElseThrow();

		ListUserPoolClientsRequest listUserPoolsClientRequest = ListUserPoolClientsRequest.builder()
				.userPoolId(userPoolId)
				.build();

		ListUserPoolClientsResponse response = cognitoClient.listUserPoolClients(listUserPoolsClientRequest);
		List<UserPoolClientDescription> clients = response.userPoolClients();

		String clientId =  clients.stream()
				.filter(userPoolClient -> userPoolClient.clientName().equals(CLIENT_NAME))
				.map(UserPoolClientDescription::clientId)
				.findFirst().orElseThrow();

		AdminInitiateAuthRequest authRequest = AdminInitiateAuthRequest.builder()
				.authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
				.clientId(clientId)
				.userPoolId(userPoolId)
				.authParameters(Map.of(
						"USERNAME", requestBody.get("email"),
						"PASSWORD", requestBody.get("password")
				)).build();

		logger.info("Calling adminInitiateAuth: " + authRequest);
		AdminInitiateAuthResponse authResult = cognitoClient.adminInitiateAuth(authRequest);
		logger.info("AdminInitiateAuthResponse: " + authResult);
		String idToken = authResult.authenticationResult().idToken();

		Map<String, String> responseBody = Map.of("accessToken", idToken);
		return createSuccessResponse(objectMapper.writeValueAsString(responseBody));
	}

	private boolean isValidSigninRequest(Map<String, String> requestBody) {
		return requestBody.containsKey("email") && requestBody.containsKey("password");
	}

	private Map<String, Object> convertDynamoDBItemToMap(Map<String, AttributeValue> item) {
		Map<String, Object> result = new HashMap<>();
		for (Map.Entry<String, AttributeValue> entry : item.entrySet()) {
			String key = entry.getKey();
			AttributeValue value = entry.getValue();
			if (value.getS() != null) {
				result.put(key, value.getS());
			} else if (value.getN() != null) {
				result.put(key, Integer.parseInt(value.getN()));
			} else if (value.getBOOL() != null) {
				result.put(key, value.getBOOL());
			} else if (value.getL() != null) {
				List<Object> list = new ArrayList<>();
				for (AttributeValue listItem : value.getL()) {
					list.add(convertDynamoDBItemToMap(Map.of(key, listItem)).get(key));
				}
				result.put(key, list);
			} else if (value.getM() != null) {
				result.put(key, convertDynamoDBItemToMap(value.getM()));
			}
		}
		return result;
	}

	private APIGatewayProxyResponseEvent handleGetTables() {
		logger.info("Start handleGetTables!");
		ScanResult scanResult = dynamoDBClient.scan(new ScanRequest().withTableName(tablesTable.getTableName()));
		List<Map<String, Object>> tables = new ArrayList<>();
		logger.info("HandleGetTables ScanResult: " + scanResult);
		List<Map<String, AttributeValue>> items = scanResult.getItems();
		logger.info("HandleGetTables items: " + items);

		for (Map<String, AttributeValue> item : items) {
			tables.add(convertDynamoDBItemToMap(item));
		}

		Map<String, Object> responseBody = Map.of("tables", tables);
		logger.info("HandleGetTables responseBody: " + responseBody);
		logger.info("HandleGetTables responseBody with serialize: " + serialize(responseBody));
		logger.info("End handleGetTables!");
		return createSuccessResponse(serialize(responseBody));
	}

	private APIGatewayProxyResponseEvent handleCreateTable(String body) throws Exception {
		logger.info("Start create table with body: " + body);

		Tables table = objectMapper.readValue(body, Tables.class);
		if (!isValidTable(table)) {
			return createErrorResponse(400, "Invalid request");
		}

		tablesTable.putItem(new Item().withPrimaryKey("id", table.getId())
				.withNumber("number", table.getNumber())
				.withNumber("places", table.getPlaces())
				.withBoolean("isVip", table.isVip())
				.withInt("minOrder", table.getMinOrder()));

		Map<String, Integer> responseBody = Map.of("id", table.getId());
		return createSuccessResponse(serialize(responseBody));
	}

	private boolean isValidTable(Tables table) {
		return table.getId() != 0 && table.getNumber() != 0 && table.getPlaces() != 0;
	}

	private APIGatewayProxyResponseEvent handleGetTable(int tableId) {

		GetItemSpec spec = new GetItemSpec().withPrimaryKey("id", tableId);
		Item item = tablesTable.getItem(spec);
		if (item == null) {
			return createErrorResponse(400, "Table not found");
		}

		Tables table = new Tables();
		table.setId(item.getInt("id"));
		table.setNumber(item.getInt("number"));
		table.setPlaces(item.getInt("places"));
		table.setIsVip(item.getBoolean("isVip"));
		table.setMinOrder(item.isPresent("minOrder") ? item.getInt("minOrder") : null);

		return createSuccessResponse(serialize(table));
	}

	private APIGatewayProxyResponseEvent handleCreateReservation(String body) throws Exception {

		logger.info("Start handleCreateReservation");
		Reservation reservation = objectMapper.readValue(body, Reservation.class);
		if (!isValidReservation(reservation)) {
			return createErrorResponse(400, "Invalid request");
		}

		ScanResult scanResultForTablesTable = dynamoDBClient.scan(new ScanRequest().withTableName(tablesTable.getTableName()));
		List<Map<String, AttributeValue>> items = scanResultForTablesTable.getItems();
		List<Map<String, Object>> tables = new ArrayList<>();
		for (Map<String, AttributeValue> item : items) {
			tables.add(convertDynamoDBItemToMap(item));
		}
		boolean tablePresent = tables.stream().anyMatch(t -> ((Integer)t.get("number")).equals(reservation.getTableNumber()));
		if (!tablePresent) {
			return createErrorResponse(400, "Table does not exist.");
		}

		Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
		expressionAttributeValues.put(":tableNumber", new AttributeValue().withN(String.valueOf(reservation.getTableNumber())));
		expressionAttributeValues.put(":date", new AttributeValue().withS(reservation.getDate()));

		ScanRequest scanRequest = new ScanRequest()
				.withTableName(reservationsTable.getTableName())
				.withFilterExpression("tableNumber = :tableNumber AND #date = :date")
				.withExpressionAttributeNames(Collections.singletonMap("#date", "date"))
				.withExpressionAttributeValues(expressionAttributeValues);

		ScanResult scanResult = dynamoDBClient.scan(scanRequest);
		for (Map<String, AttributeValue> item : scanResult.getItems()) {
			String existingSlotTimeStart = item.get("slotTimeStart").getS();
			String existingSlotTimeEnd = item.get("slotTimeEnd").getS();

			if (isTimeSlotOverlap(reservation.getSlotTimeStart(), reservation.getSlotTimeEnd(), existingSlotTimeStart, existingSlotTimeEnd)) {
				return createErrorResponse(400, "Reservation time slot overlaps with an existing reservation.");
			}
		}

		reservation.setId(UUID.randomUUID().toString());
		reservationsTable.putItem(new Item()
				.withPrimaryKey("id", reservation.getId())
				.withNumber("tableNumber", reservation.getTableNumber())
				.withString("clientName", reservation.getClientName())
				.withString("phoneNumber", reservation.getPhoneNumber())
				.withString("date", reservation.getDate())
				.withString("slotTimeStart", reservation.getSlotTimeStart())
				.withString("slotTimeEnd", reservation.getSlotTimeEnd()));

		Map<String, String> responseBody = Map.of("reservationId", reservation.getId());
		return createSuccessResponse(serialize(responseBody));
	}

	private boolean isTimeSlotOverlap(String newStart, String newEnd, String existingStart, String existingEnd) {
		return (newStart.compareTo(existingEnd) < 0 && newEnd.compareTo(existingStart) > 0);
	}

	private boolean isValidReservation(Reservation reservation) {
		return reservation.getTableNumber() != 0 &&
				reservation.getClientName() != null &&
				reservation.getPhoneNumber() != null &&
				reservation.getDate() != null &&
				reservation.getSlotTimeStart() != null &&
				reservation.getSlotTimeEnd() != null;
	}

	private APIGatewayProxyResponseEvent handleGetReservations() {
		logger.info("Start handleGetReservations!");

		ScanResult scanResult = dynamoDBClient.scan(new ScanRequest().withTableName(reservationsTable.getTableName()));
		List<Map<String, Object>> reservations = new ArrayList<>();
		logger.info("HandleGetReservations ScanResult: " + scanResult);
		List<Map<String, AttributeValue>> items = scanResult.getItems();
		logger.info("HandleGetReservations items: " + items);

		for (Map<String, AttributeValue> item : items) {
			reservations.add(convertDynamoDBItemToMap(item));
		}

		Map<String, Object> responseBody = Map.of("reservations", reservations);
		logger.info("HandleGetReservations responseBody: " + responseBody);
		logger.info("End handleGetReservations!");
		return createSuccessResponse(serialize(responseBody));
	}

	private APIGatewayProxyResponseEvent createSuccessResponse(String body) {
		return new APIGatewayProxyResponseEvent()
				.withStatusCode(SUCCESS_STATUS_CODE)
				.withBody(body);
	}

	private APIGatewayProxyResponseEvent createErrorResponse(int statusCode, String message) {
		return new APIGatewayProxyResponseEvent()
				.withStatusCode(statusCode)
				.withBody(String.format("{\"message\":\"%s\"}", message));
	}

	private String serialize(Object obj) {
		try {
			return objectMapper.writeValueAsString(obj);
		} catch (Exception e) {
			return "{}";
		}
	}


}
