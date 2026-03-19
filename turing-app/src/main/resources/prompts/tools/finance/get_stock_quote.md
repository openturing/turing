Gets the current stock price and market data for a given ticker symbol.
Use this tool when the user asks about stock prices, market data, or financial quotes.
Args:
    symbol (str): Stock ticker symbol (e.g., 'AAPL' for Apple, 'GOOGL' for Google, 'PETR4.SA' for Petrobras, '^BVSP' for Bovespa index, '^GSPC' for S&P 500, '^DJI' for Dow Jones, 'BTC-USD' for Bitcoin, 'BRL=X' for USD/BRL exchange rate). Required.
    range (str): Time range for historical data. Options: '1d', '5d', '1mo', '3mo', '6mo', '1y'. Default: '5d'.
Returns:
    Current price, change, volume, and recent price history.
