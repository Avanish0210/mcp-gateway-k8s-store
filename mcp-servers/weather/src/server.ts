import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

const server = new McpServer({
  name: "weather-mcp-server",
  version: "1.0.0",
});

const GEOCODING_URL =
  "https://geocoding-api.open-meteo.com/v1/search";

const WEATHER_URL =
  "https://api.open-meteo.com/v1/forecast";

async function fetchJson(
  url: string,
  params: Record<string, string>
): Promise<any> {
  const query = new URLSearchParams(params);
  const response = await fetch(`${url}?${query.toString()}`);

  if (!response.ok) {
    throw new Error(
      `Weather API request failed: ${response.status} ${response.statusText}`
    );
  }

  return response.json();
}

async function findLocation(name: string) {
  const result = await fetchJson(GEOCODING_URL, {
    name,
    count: "1",
    language: "en",
    format: "json",
  });

  if (!result.results?.length) {
    throw new Error(`Location not found: ${name}`);
  }

  return result.results[0];
}

function getWeatherDescription(code: number): string {
  const descriptions: Record<number, string> = {
    0: "Clear sky",
    1: "Mainly clear",
    2: "Partly cloudy",
    3: "Overcast",
    45: "Fog",
    48: "Depositing rime fog",
    51: "Light drizzle",
    53: "Moderate drizzle",
    55: "Dense drizzle",
    61: "Slight rain",
    63: "Moderate rain",
    65: "Heavy rain",
    71: "Slight snow",
    73: "Moderate snow",
    75: "Heavy snow",
    80: "Slight rain showers",
    81: "Moderate rain showers",
    82: "Violent rain showers",
    95: "Thunderstorm",
    96: "Thunderstorm with slight hail",
    99: "Thunderstorm with heavy hail",
  };

  return descriptions[code] ?? `Weather code ${code}`;
}

// Tool 1: Search for a location.
server.registerTool(
  "search_location",
  {
    title: "Search Location",
    description:
      "Find a location and return its coordinates and timezone.",
    inputSchema: {
      location: z.string().min(1).describe("City or place name"),
    },
  },
  async ({ location }) => {
    try {
      const result = await fetchJson(GEOCODING_URL, {
        name: location,
        count: "5",
        language: "en",
        format: "json",
      });

      const locations = (result.results ?? []).map((item: any) => ({
        name: item.name,
        country: item.country,
        admin1: item.admin1,
        latitude: item.latitude,
        longitude: item.longitude,
        timezone: item.timezone,
      }));

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify({ locations }, null, 2),
          },
        ],
      };
    } catch (error) {
      return {
        isError: true,
        content: [
          {
            type: "text",
            text:
              error instanceof Error
                ? error.message
                : "Unable to search locations.",
          },
        ],
      };
    }
  }
);

// Tool 2: Get current weather.
server.registerTool(
  "get_current_weather",
  {
    title: "Get Current Weather",
    description:
      "Get current temperature, humidity, wind speed and weather conditions for a city.",
    inputSchema: {
      location: z.string().min(1).describe("City or place name"),
    },
  },
  async ({ location }) => {
    try {
      const place = await findLocation(location);

      const weather = await fetchJson(WEATHER_URL, {
        latitude: String(place.latitude),
        longitude: String(place.longitude),
        current:
          "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation,weather_code,wind_speed_10m",
        timezone: "auto",
      });

      const current = weather.current;

      const result = {
        location: {
          name: place.name,
          country: place.country,
          latitude: place.latitude,
          longitude: place.longitude,
          timezone: weather.timezone,
        },
        current: {
          time: current.time,
          temperatureC: current.temperature_2m,
          feelsLikeC: current.apparent_temperature,
          humidityPercent: current.relative_humidity_2m,
          windSpeedKmh: current.wind_speed_10m,
          precipitationMm: current.precipitation,
          isDay: current.is_day === 1,
          weatherCode: current.weather_code,
          conditions: getWeatherDescription(current.weather_code),
        },
        units: weather.current_units,
      };

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(result, null, 2),
          },
        ],
      };
    } catch (error) {
      return {
        isError: true,
        content: [
          {
            type: "text",
            text:
              error instanceof Error
                ? error.message
                : "Unable to retrieve current weather.",
          },
        ],
      };
    }
  }
);

// Tool 3: Get a daily forecast.
server.registerTool(
  "get_weather_forecast",
  {
    title: "Get Weather Forecast",
    description:
      "Get a daily weather forecast for a city for 1 to 7 days.",
    inputSchema: {
      location: z.string().min(1).describe("City or place name"),
      days: z.number().int().min(1).max(7).default(5),
    },
  },
  async ({ location, days }) => {
    try {
      const place = await findLocation(location);

      const weather = await fetchJson(WEATHER_URL, {
        latitude: String(place.latitude),
        longitude: String(place.longitude),
        daily:
          "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max",
        forecast_days: String(days),
        timezone: "auto",
      });

      const daily = weather.daily;

      const forecast = daily.time.map(
        (date: string, index: number) => ({
          date,
          conditions: getWeatherDescription(
            daily.weather_code[index]
          ),
          weatherCode: daily.weather_code[index],
          maxTemperatureC:
            daily.temperature_2m_max[index],
          minTemperatureC:
            daily.temperature_2m_min[index],
          precipitationProbabilityPercent:
            daily.precipitation_probability_max[index],
          maxWindSpeedKmh:
            daily.wind_speed_10m_max[index],
        })
      );

      const result = {
        location: {
          name: place.name,
          country: place.country,
          latitude: place.latitude,
          longitude: place.longitude,
          timezone: weather.timezone,
        },
        forecast,
        units: weather.daily_units,
      };

      return {
        content: [
          {
            type: "text",
            text: JSON.stringify(result, null, 2),
          },
        ],
      };
    } catch (error) {
      return {
        isError: true,
        content: [
          {
            type: "text",
            text:
              error instanceof Error
                ? error.message
                : "Unable to retrieve the weather forecast.",
          },
        ],
      };
    }
  }
);

async function main() {
  const transport = new StdioServerTransport();

  await server.connect(transport);

  // Do not print logs to stdout. stdout carries MCP protocol messages.
  console.error("Weather MCP server started.");
}

main().catch((error) => {
  console.error("Failed to start Weather MCP server:", error);
  process.exit(1);
});