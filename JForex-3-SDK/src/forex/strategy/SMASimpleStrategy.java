package forex.strategy;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class SMASimpleStrategy implements IStrategy {

	private final String id = generateStrategyId(this.getClass().getSimpleName().toUpperCase());

	private IEngine engine;
	private IHistory history;
	private IIndicators indicators;
	private int counter = 0;
	private static final int MAX_ID_LENGTH = 5;

	private IConsole console;

	@Configurable("Instrument")
	private Instrument instrument = Instrument.EURUSD;// Instrument.EURUSD;
	@Configurable("Period")
	private Period selectedPeriod = Period.FIVE_MINS; // - Period.FOUR_HOURS;
	@Configurable("SMA filter")
	private Filter indicatorFilter = Filter.WEEKENDS;
	@Configurable("Amount")
	private double defaultTradeAmount = 0.1;
	@Configurable("slippage:")
	public int slippage = 0;

	/**
	 * Mes conservador
	 */
	@Configurable("Stop loss")
	private int stopLossPips = 75;
	@Configurable("Take profit")
	private int takeProfitPips = 30;

	// private IFeedDescriptor feedDescriptor;

	private double lastEquity = 0;

	public void onStart(IContext context) throws JFException {
		this.engine = context.getEngine();
		this.history = context.getHistory();
		this.indicators = context.getIndicators();
		this.lastEquity = history.getEquity();
		this.console = context.getConsole();
		// this.feedDescriptor = new TimePeriodAggregationFeedDescriptor(instrument,
		// selectedPeriod, OfferSide.ASK,
		// indicatorFilter);
	}

	public void onAccount(IAccount account) throws JFException {
	}

	public void onMessage(IMessage message) throws JFException {
		printOnMessage(console, message, id);
	}

	public void onStop() throws JFException {
		double closePL = 0;
		// close all orders
		for (IOrder order : engine.getOrders()) {
			IOrder orderToClose = engine.getOrder(order.getLabel());
			orderToClose.close();
			closePL += orderToClose.getProfitLossInAccountCurrency();
		}
		System.err.println("TOTAL Closed Order: " + closePL + "$");
	}

	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {

		if (!instrument.equals(instrument) || !period.equals(selectedPeriod))
			return;

		IBar prevBar = history.getBar(instrument, selectedPeriod, OfferSide.BID, 2);

		// System.err
		// .println(new Date(bidBar.getTime()).toString() + " - PREV: " + new
		// Date(prevBar.getTime()).toString());

		// ADX
		double adx = indicators.adx(instrument, selectedPeriod, OfferSide.BID, 14, 1);

		// Parabollic SAR
		double yesterdaySAR = indicators.sar(instrument, period, OfferSide.BID, 0.02, 0.2, 2);
		double todaySAR = indicators.sar(instrument, period, OfferSide.BID, 0.02, 0.2, 1);

		boolean isSARSell = (prevBar.getLow() > yesterdaySAR && bidBar.getLow() < todaySAR);
		boolean isSARBuy = (prevBar.getHigh() < yesterdaySAR && bidBar.getHigh() > todaySAR);

		boolean isSMABuy = isSMACrossBuyNoFeed(indicators, instrument, period, OfferSide.BID);
		if (isSMABuy && adx >= 30 && isSARBuy) {
			submitOrder(OrderCommand.BUY, bidBar, askBar);
		}

		boolean isSMASell = isSMACrossSellNoFeed(indicators, instrument, period, OfferSide.BID);
		if (isSMASell && adx >= 30 && isSARSell) {
			submitOrder(OrderCommand.SELL, bidBar, askBar);
		}

		// if (/* isSMABuy || (adx >= 30) || */ isSARBuy) {
		// System.err.println("isSMABuy: " + isSMABuy + " - isADX: " + (adx >= 30) + " -
		// isSARSell: " + isSARBuy);
		// }
		// if (/* isSMASell || (adx >= 30) || */ isSARSell) {
		// System.err.println("isSMASell: " + isSMASell + " - isADX: " + (adx >= 30) + "
		// - isSARSell: " + isSARSell);
		// }
		//
		// // AND [Yesterday's Daily Low > Yesterday's Daily Parabolic SAR(0.01,0.2)]
		// // AND [Daily Low < Daily Parabolic SAR(0.01,0.2)]
		//
		// if ((prevBar.getLow() > yesterdaySAR && bidBar.getLow() < todaySAR) &&
		// isSMASell) {
		// System.err.println("SAR IN --> SELL");
		// submitOrder(OrderCommand.SELL);
		// }
		//
		// // AND [Yesterday's Daily High < Yesterday's Daily Parabolic SAR(0.01,0.2)]
		// // AND [Daily High > Daily Parabolic SAR(0.01,0.2)]
		// if ((prevBar.getHigh() < yesterdaySAR && bidBar.getHigh() > todaySAR) &&
		// isSMABuy) {
		// System.err.println("SAR IN --> BUY");
		// submitOrder(OrderCommand.BUY);
		// }
	}

	public static boolean isSMACrossBuyNoFeed(IIndicators indicators, Instrument instrument, Period period,
			OfferSide offerSide) throws JFException {

		int candlesBefore = 2;
		int candlesAfter = 0;

		double sma10 = indicators.sma(instrument, period, offerSide, AppliedPrice.CLOSE, 10, candlesBefore);
		double sma20 = indicators.sma(instrument, period, offerSide, AppliedPrice.CLOSE, 20, candlesBefore);
		double sma50 = indicators.sma(instrument, period, offerSide, AppliedPrice.CLOSE, 50, candlesBefore);
		double sma100 = indicators.sma(instrument, period, offerSide, AppliedPrice.CLOSE, 100, candlesBefore);
		double sma200 = indicators.sma(instrument, period, offerSide, AppliedPrice.CLOSE, 200, candlesBefore);

		return ((sma10 > sma20) && (sma20 > sma50) && (sma50 > sma100) && (sma100 > sma200));
	}

	public static boolean isSMACrossSellNoFeed(IIndicators indicators, Instrument instrument, Period period,
			OfferSide offerSide) throws JFException {

		int candlesBefore = 2;
		int candlesAfter = 0;

		double sma10 = indicators.sma(instrument, period, offerSide, AppliedPrice.CLOSE, 10, candlesBefore);
		double sma20 = indicators.sma(instrument, period, offerSide, AppliedPrice.CLOSE, 20, candlesBefore);
		double sma50 = indicators.sma(instrument, period, offerSide, AppliedPrice.CLOSE, 50, candlesBefore);
		double sma100 = indicators.sma(instrument, period, offerSide, AppliedPrice.CLOSE, 100, candlesBefore);
		double sma200 = indicators.sma(instrument, period, offerSide, AppliedPrice.CLOSE, 200, candlesBefore);

		return ((sma10 < sma20) && (sma20 < sma50) && (sma50 < sma100) && (sma100 < sma200));
	}

	/**
	 * If equity is lower than or equal to lastEquity, it calculates the amount
	 * according to the following formula: amount = min(Equity/20000.0,
	 * defaultTradeAmount). But if equity is greater than lastEquity it calculates
	 * the amount according to the following formula: amount = amount +
	 * defaultTradeAmount. 2. Updates lastEquity to the value of current equity
	 */
	private double calculateAmountFromEquity() {
		double newamount = defaultTradeAmount;

		if (history.getEquity() <= lastEquity) {
			// amount = min(Equity/20000.0,* defaultTradeAmount)
			newamount = Math.min(history.getEquity() / 20000.0, defaultTradeAmount);
		} else {
			newamount = newamount + defaultTradeAmount;
		}
		lastEquity = history.getEquity();
		return newamount;
	}

	/**
	 * 
	 * @param orderCmd
	 * @return
	 * @throws JFException
	 */
	private IOrder submitOrder(OrderCommand orderCmd, final IBar bidBar, final IBar askBar) throws JFException {

		double stopLossPrice, takeProfitPrice;

		// Calculating stop loss and take profit prices
		if (orderCmd == OrderCommand.BUY) {
			// stopLossPrice = history.getLastTick(this.instrument).getBid() -
			// getPipPrice(this.stopLossPips);
			// takeProfitPrice = history.getLastTick(this.instrument).getBid() +
			// getPipPrice(this.takeProfitPips);
			final double atr = indicators.atr(instrument, Period.FOUR_HOURS, OfferSide.BID, 21, 1);
			takeProfitPrice = roundDouble((atr * 3) + bidBar.getOpen(), 5);
			stopLossPrice = roundDouble(bidBar.getOpen() - (atr * 1.5), 5);
			System.err.println("--> BUYING [" + history.getLastTick(this.instrument).getBid() + "]: stopLossPrice="
					+ stopLossPrice + " - takeProfitPrice:" + takeProfitPrice + "\n");
		} else {
			// stopLossPrice = history.getLastTick(this.instrument).getAsk() +
			// getPipPrice(this.stopLossPips);
			// takeProfitPrice = history.getLastTick(this.instrument).getAsk() -
			// getPipPrice(this.takeProfitPips);
			final double atr = indicators.atr(instrument, Period.FOUR_HOURS, OfferSide.BID, 21, 1);
			takeProfitPrice = roundDouble(askBar.getOpen() - (atr * 3), 5);
			stopLossPrice = roundDouble((atr * 1.5) + askBar.getOpen(), 5);

			System.err.println("--> SELLING [" + history.getLastTick(this.instrument).getAsk() + "]: stopLossPrice="
					+ stopLossPrice + " - takeProfitPrice:" + takeProfitPrice + "\n");
		}

		// double newamount = calculateAmountFromEquity();
		double newamount = defaultTradeAmount; // Ha donat mes beneficis q el calcul from equity
		final String orderLabel = getLabel(instrument, id, ++counter);

		return engine.submitOrder(orderLabel, this.instrument, orderCmd, newamount, 0, slippage, 0/* stopLossPrice */,
				takeProfitPrice);
	}

	private double getPipPrice(int pips) {
		return pips * this.instrument.getPipValue();
	}

	public static String getLabel(final Instrument instrument, final String id, final int counter) {
		return id + instrument.name().substring(0, 2) + instrument.name().substring(3, 5)
				+ String.format("%8d", counter).replace(" ", "0");
	}

	public static void printOnMessage(final IConsole console, final IMessage message, final String id)
			throws JFException {
		// Print messages, but related to own orders
		if (message.getOrder() != null && message.getOrder().getLabel().substring(0, MAX_ID_LENGTH).equals(id)) {
			String orderLabel = message.getOrder().getLabel();
			IMessage.Type messageType = message.getType();
			switch (messageType) {
			// Ignore the following
			case ORDER_FILL_OK:
			case ORDER_CHANGED_OK:
				break;
			case ORDER_SUBMIT_OK:
				break;
			case ORDER_CLOSE_OK:
				console.getErr()
						.println(orderLabel + " : " + message.getOrder().getOrderCommand().toString() + " : "
								+ messageType + ": " + "Pips: " + message.getOrder().getProfitLossInPips() + " - $$$: "
								+ message.getOrder().getProfitLossInAccountCurrency() + "$");
				break;
			case ORDERS_MERGE_OK:
				console.getInfo().println(orderLabel + " " + messageType);
				break;
			case NOTIFICATION:
				console.getNotif().println(orderLabel + " " + message.getContent().replaceAll(".*-Order", "Order"));
				break;
			case ORDER_CHANGED_REJECTED:
			case ORDER_CLOSE_REJECTED:
			case ORDER_FILL_REJECTED:
			case ORDER_SUBMIT_REJECTED:
			case ORDERS_MERGE_REJECTED: {
				console.getWarn().println(orderLabel + " " + messageType + " - size:" + message.getReasons().size()
						+ " - cont:" + message.getContent());

				message.getReasons().forEach((reason) -> {
					console.getWarn().println("Reason:" + reason.toString());
				});
			}
				break;
			default:
				console.getErr().println(orderLabel + " *" + messageType + "* " + message.getContent());
				break;
			}
		}
	}

	public static String generateStrategyId(final String className) {
		return className.substring(0, MAX_ID_LENGTH);
	}

	private double roundDouble(double num, int dec) {
		return Math.round(num * Math.pow(10, dec)) / Math.pow(10, dec);
	}

	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}
}
