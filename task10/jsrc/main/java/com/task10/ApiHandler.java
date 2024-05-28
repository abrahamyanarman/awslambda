package com.task10;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.AdminCreateUserRequest;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.AuthFlowType;
import com.amazonaws.services.cognitoidp.model.GetUserRequest;
import com.amazonaws.services.cognitoidp.model.InitiateAuthRequest;
import com.amazonaws.services.cognitoidp.model.InitiateAuthResult;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@LambdaHandler(lambdaName = "api_handler",
	roleName = "api_handler-role",
	isPublishVersion = false,
	logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	private static final String USER_POOL_ID = "cmtr-a7a5b08f-simple-booking-userpool-test";
	private static final String CLIENT_ID = "cmtr-a7a5b08f-task10-client";

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
	private final Table tablesTable = dynamoDB.getTable("Tables");
	private final Table reservationsTable = dynamoDB.getTable("Reservations");
	private final AWSCognitoIdentityProvider cognitoClient = AWSCognitoIdentityProviderClientBuilder.defaultClient();
	private final ObjectMapper objectMapper = new ObjectMapper();

	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
		String httpMethod = event.getHttpMethod();
		String path = event.getPath();
		String body = event.getBody();
		Map<String, String> headers = event.getHeaders();
		APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
		response.setHeaders(Collections.singletonMap("Content-Type", "application/json"));

		try {
			if (SIGN_UP_PATH.equals(path) && POST.equalsIgnoreCase(httpMethod)) {
				return handleSignup(body);
			} else if (SIGN_IN_PATH.equals(path) && POST.equalsIgnoreCase(httpMethod)) {
				return handleSignin(body);
			} else if (TABLES_PATH.equals(path) && GET.equalsIgnoreCase(httpMethod)) {
				return handleGetTables(headers);
			} else if (TABLES_PATH.equals(path) && POST.equalsIgnoreCase(httpMethod)) {
				return handleCreateTable(headers, body);
			} else if (path.startsWith(START_WITH_TABLES_PATH) && GET.equalsIgnoreCase(httpMethod)) {
				int tableId = Integer.parseInt(path.split("/")[2]);
				return handleGetTable(headers, tableId);
			} else if (RESERVATIONS_PATH.equals(path) && POST.equalsIgnoreCase(httpMethod)) {
				return handleCreateReservation(headers, body);
			} else if (RESERVATIONS_PATH.equals(path) && GET.equalsIgnoreCase(httpMethod)) {
				return handleGetReservations(headers);
			} else {
				response.setStatusCode(400);
				response.setBody("{\"message\":\"Bad Request\"}");
			}
		} catch (Exception e) {
			response.setStatusCode(500);
			response.setBody("{\"message\":\"Internal Server Error\"}");
		}

		return response;

	}

	private APIGatewayProxyResponseEvent handleSignup(String body) throws Exception {
		Map<String, String> requestBody = objectMapper.readValue(body, Map.class);
		if (!isValidSignupRequest(requestBody)) {
			return createErrorResponse(400, "Invalid request");
		}

		AdminCreateUserRequest createUserRequest = new AdminCreateUserRequest()
				.withUserPoolId(USER_POOL_ID)
				.withUsername(requestBody.get("email"))
				.withUserAttributes(
						new AttributeType().withName("email").withValue(requestBody.get("email")),
						new AttributeType().withName("email_verified").withValue("true"),
						new AttributeType().withName("given_name").withValue(requestBody.get("firstName")),
						new AttributeType().withName("family_name").withValue(requestBody.get("lastName"))
				)
				.withTemporaryPassword(requestBody.get("password"))
				.withMessageAction("SUPPRESS");

		cognitoClient.adminCreateUser(createUserRequest);

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
		String passwordRegex = "^(?=.*[a-zA-Z])(?=.*\\d)(?=.*[$%^*]).{12,}$";
		Pattern pattern = Pattern.compile(passwordRegex);
		Matcher matcher = pattern.matcher(password);
		return matcher.matches();
	}

	private APIGatewayProxyResponseEvent handleSignin(String body) throws Exception {
		Map<String, String> requestBody = objectMapper.readValue(body, Map.class);
		if (!isValidSigninRequest(requestBody)) {
			return createErrorResponse(400, "Invalid request");
		}

		InitiateAuthRequest authRequest = new InitiateAuthRequest()
				.withAuthFlow(AuthFlowType.USER_PASSWORD_AUTH)
				.withClientId(CLIENT_ID)
				.withAuthParameters(Map.of(
						"USERNAME", requestBody.get("email"),
						"PASSWORD", requestBody.get("password")
				));

		InitiateAuthResult authResult = cognitoClient.initiateAuth(authRequest);
		String idToken = authResult.getAuthenticationResult().getIdToken();

		Map<String, String> responseBody = Map.of("accessToken", idToken);
		return createSuccessResponse(objectMapper.writeValueAsString(responseBody));
	}

	private boolean isValidSigninRequest(Map<String, String> requestBody) {
		return requestBody.containsKey("email") && requestBody.containsKey("password");
	}
	private APIGatewayProxyResponseEvent handleGetTables(Map<String, String> headers) {
		if (isNotAuthorized(headers)) {
			return createErrorResponse(403, "Unauthorized");
		}

		ScanResult scanResult = dynamoDBClient.scan(new ScanRequest().withTableName("Tables"));
		List<Map<String, AttributeValue>> items = scanResult.getItems();

		Map<String, List<Map<String, AttributeValue>>> responseBody = Map.of("tables", items);
		return createSuccessResponse(serialize(responseBody));
	}

	private APIGatewayProxyResponseEvent handleCreateTable(Map<String, String> headers, String body) throws Exception {
		if (isNotAuthorized(headers)) {
			return createErrorResponse(403, "Unauthorized");
		}

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

	private APIGatewayProxyResponseEvent handleGetTable(Map<String, String> headers, int tableId) {
		if (isNotAuthorized(headers)) {
			return createErrorResponse(403, "Unauthorized");
		}

		GetItemSpec spec = new GetItemSpec().withPrimaryKey("id", tableId);
		Item item = tablesTable.getItem(spec);
		if (item == null) {
			return createErrorResponse(400, "Table not found");
		}

		Tables table = new Tables();
		table.setId(item.getInt("id"));
		table.setNumber(item.getInt("number"));
		table.setPlaces(item.getInt("places"));
		table.setVip(item.getBoolean("isVip"));
		table.setMinOrder(item.isPresent("minOrder") ? item.getInt("minOrder") : null);

		return createSuccessResponse(serialize(table));
	}

	private APIGatewayProxyResponseEvent handleCreateReservation(Map<String, String> headers, String body) throws Exception {
		if (isNotAuthorized(headers)) {
			return createErrorResponse(403, "Unauthorized");
		}

		Reservation reservation = objectMapper.readValue(body, Reservation.class);
		if (!isValidReservation(reservation)) {
			return createErrorResponse(400, "Invalid request");
		}

		reservation.setReservationId(UUID.randomUUID().toString());
		reservationsTable.putItem(new Item()
				.withPrimaryKey("reservationId", reservation.getReservationId())
				.withNumber("tableNumber", reservation.getTableNumber())
				.withString("clientName", reservation.getClientName())
				.withString("phoneNumber", reservation.getPhoneNumber())
				.withString("date", reservation.getDate())
				.withString("slotTimeStart", reservation.getSlotTimeStart())
				.withString("slotTimeEnd", reservation.getSlotTimeEnd()));

		Map<String, String> responseBody = Map.of("reservationId", reservation.getReservationId());
		return createSuccessResponse(serialize(responseBody));
	}
	private boolean isValidReservation(Reservation reservation) {
		return reservation.getTableNumber() != 0 &&
				reservation.getClientName() != null &&
				reservation.getPhoneNumber() != null &&
				reservation.getDate() != null &&
				reservation.getSlotTimeStart() != null &&
				reservation.getSlotTimeEnd() != null;
	}

	private APIGatewayProxyResponseEvent handleGetReservations(Map<String, String> headers) {
		if (isNotAuthorized(headers)) {
			return createErrorResponse(403, "Unauthorized");
		}

		ScanResult scanResult = dynamoDBClient.scan(new ScanRequest().withTableName("Reservations"));
		List<Map<String, AttributeValue>> items = scanResult.getItems();

		Map<String, List<Map<String, AttributeValue>>> responseBody = Map.of("reservations", items);
		return createSuccessResponse(serialize(responseBody));
	}

	private boolean isNotAuthorized(Map<String, String> headers) {
		if (!headers.containsKey("Authorization")) {
			return true;
		}

		String token = headers.get("Authorization").split(" ")[1];
		try {
			GetUserRequest getUserRequest = new GetUserRequest().withAccessToken(token);
			cognitoClient.getUser(getUserRequest);
			return false;
		} catch (Exception e) {
			return true;
		}
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
