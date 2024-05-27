package am.awslambda.task08;

import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class OpenMeteoAPILightweightSDK {
    private static final String BASE_URL = "https://api.open-meteo.com/v1/forecast";
    private final double latitude;
    private final double longitude;
    private final HttpClient httpClient;

    public OpenMeteoAPILightweightSDK(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.httpClient = HttpClient.newHttpClient();
    }

    public JSONObject getWeatherForecast() throws IOException, InterruptedException {
        var url = String.format("%s?latitude=%.4f&longitude=%.4f&current_weather=true&hourly=temperature_2m", BASE_URL, latitude, longitude);
        var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return new JSONObject(response.body());
        } else {
            throw new IOException("Failed to fetch weather data: " + response.body());
        }
    }
}
