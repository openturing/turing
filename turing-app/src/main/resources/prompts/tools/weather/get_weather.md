Gets the current weather and forecast for a city or location.
Use this tool when the user asks about the weather, temperature, or forecast for a place.
Args:
    location (str): City name, optionally with country (e.g., 'São Paulo', 'London, UK', 'New York'). Required.
    days (int): Number of forecast days (1-7). Default: 3.
Returns:
    Current weather conditions and daily forecast including temperature, humidity, wind speed, and weather description.
