package forex.strategy;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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

public class SmaFibStrategyAIO implements IStrategy {

	private static final int ADX_THRESHOLD = 40;

	private final String id = generateStrategyId(this.getClass().getSimpleName().toUpperCase());

	private IEngine engine;
	private IHistory history;
	private IIndicators indicators;
	private IAccount account;
	private int counter = 0;
	private static final int MAX_ID_LENGTH = 5;

	IOrder lastOrder = null;

	private IConsole console;

	@Configurable("Instrument")
	private Instrument instrument = Instrument.EURUSD;// Instrument.EURUSD;
	@Configurable("Period")
	private Period selectedPeriod = Period.FIFTEEN_MINS; // - Period.FOUR_HOURS;
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

	private double lastEquity = 0;

	public void onStart(IContext context) throws JFException {
		this.engine = context.getEngine();
		this.history = context.getHistory();
		this.indicators = context.getIndicators();
		this.lastEquity = history.getEquity();
		this.console = context.getConsole();
		this.account = context.getAccount();
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
		console.getErr().println("TOTAL Closed Order: " + closePL + "$");
	}

	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (!instrument.equals(this.instrument) || !period.equals(selectedPeriod))
			return;

		final IBar prevBar = history.getBar(instrument, selectedPeriod, OfferSide.BID, 2);

		// System.err
		// .println(new Date(bidBar.getTime()).toString() + " - PREV: " + new
		// Date(prevBar.getTime()).toString());

		// ADX
		double adx = indicators.adx(instrument, Period.ONE_HOUR/* selectedPeriod */, OfferSide.BID, 14, 1);

		// double atr3 = indicators.atr(instrument, selectedPeriod, OfferSide.BID, 14,
		// 3);
		// double atr2 = indicators.atr(instrument, selectedPeriod, OfferSide.BID, 14,
		// 2);
		// double atr1 = indicators.atr(instrument, selectedPeriod, OfferSide.BID, 14,
		// 1);
		// boolean isATR = (atr3 < atr2) && (atr2 < atr1);

		// yesterday SAR > yesterday High Price && today SAR < today low price
		// Parabollic SAR
		double yesterdaySAR = indicators.sar(instrument, period, OfferSide.BID, 0.01, 0.1, 2);
		double todaySAR = indicators.sar(instrument, period, OfferSide.BID, 0.01, 0.1, 1);

		boolean isSARBuy = (yesterdaySAR > prevBar.getHigh() && todaySAR < bidBar.getLow());
		boolean isSARSell = (yesterdaySAR < prevBar.getLow() && todaySAR > bidBar.getHigh());

		boolean isSMABuy = isSMACrossBuyNoFeed(indicators, instrument, period, OfferSide.BID);
		if (isSMABuy && /* adx >= ADX_THRESHOLD && */ isSARBuy && isRiskManagementOK()) {

			long timeBetweenOrders = (lastOrder == null ? 0 : (bidBar.getTime() - lastOrder.getCreationTime()));
			long timeToWait = period.getInterval() * 5;
			if (timeBetweenOrders != 0 && timeBetweenOrders <= timeToWait) {
				console.getErr().println("XXXXXXXXXXX  WAIT MOR TIME!!!!");
				return;
			}
			lastOrder = submitOrder(OrderCommand.BUY);
		}

		boolean isSMASell = isSMACrossSellNoFeed(indicators, instrument, period, OfferSide.BID);
		if (isSMASell && /* adx >= ADX_THRESHOLD && */ isSARSell && isRiskManagementOK()) {

			long timeBetweenOrders = (lastOrder == null ? 0 : (bidBar.getTime() - lastOrder.getCreationTime()));
			long timeToWait = period.getInterval() * 5;
			if (timeBetweenOrders != 0 && timeBetweenOrders <= timeToWait) {
				console.getErr().println("XXXXXXXXXXX  WAIT MOR TIME!!!!");
				return;
			}
			lastOrder = submitOrder(OrderCommand.SELL);
		}
	}

	/**
	 * 
	 * @param orderCmd
	 * @return
	 * @throws JFException
	 */
	private IOrder submitOrder(OrderCommand orderCmd) throws JFException {

		double stopLossPrice, takeProfitPrice;

		// Calculating stop loss and take profit prices
		ITick lastTick = history.getLastTick(this.instrument);
		if (orderCmd == OrderCommand.BUY) {
			stopLossPrice = calculateBuyStopLossFromFib();
			takeProfitPrice = calculateBuyTakeProfitFromFib();

			double oldTakeProfit = history.getLastTick(this.instrument).getBid() + getPipPrice(this.takeProfitPips);
			if (oldTakeProfit > takeProfitPrice) {
				takeProfitPrice = oldTakeProfit;
			}

			double oldStopLoss = lastTick.getBid() - getPipPrice(this.stopLossPips);
			if (oldStopLoss < stopLossPrice) {
				stopLossPrice = oldStopLoss;
			}

			// double newamount = calculateAmountFromEquity();
			double newamount = defaultTradeAmount; // Ha donat mes beneficis q el calcul from equity
			final String orderLabel = getLabel(instrument, id, ++counter);

			console.getErr().println("--> BUYING.id: " + orderLabel + " [" + lastTick.getBid() + "]: takeProfitPrice="
					+ takeProfitPrice + " - stopLossPrice:" + stopLossPrice + "\n");
			printOnSubmit();
			return engine.submitOrder(orderLabel, this.instrument, OrderCommand.BUYSTOP, newamount, lastTick.getBid(),
					slippage, stopLossPrice, takeProfitPrice, lastTick.getTime() + Period.WEEKLY.getInterval());

		} else if (orderCmd == OrderCommand.SELL) {
			stopLossPrice = calculateSellStopLossFromFib();
			takeProfitPrice = calculateTakeProfitFromFibSell();

			double oldTakeProfit = history.getLastTick(this.instrument).getBid() - getPipPrice(this.takeProfitPips);
			if (oldTakeProfit < takeProfitPrice) {
				takeProfitPrice = oldTakeProfit;
			}

			double oldStopLoss = lastTick.getBid() + getPipPrice(this.stopLossPips);
			if (oldStopLoss > stopLossPrice) {
				stopLossPrice = oldStopLoss;
			}

			// double newamount = calculateAmountFromEquity();
			double newamount = defaultTradeAmount; // Ha donat mes beneficis q el calcul from equity
			final String orderLabel = getLabel(instrument, id, ++counter);

			console.getErr().println("--> SELLING.id: " + orderLabel + " [" + lastTick.getBid() + "]: takeProfitPrice="
					+ takeProfitPrice + " - stopLossPrice:" + stopLossPrice + "\n");
			printOnSubmit();

			return engine.submitOrder(orderLabel, this.instrument, OrderCommand.SELLSTOP, newamount, lastTick.getAsk(),
					slippage, stopLossPrice, takeProfitPrice, lastTick.getTime() + Period.WEEKLY.getInterval());

		} else {
			return null;
		}
	}

	/**
	 * 1.- Use of Levergae > 10%
	 * 
	 * @return
	 */
	private boolean isRiskManagementOK() {
		return account.getUseOfLeverage() < 10;
	}

	private void printOnSubmit() throws JFException {
		// console.getErr().println("Amount at risk: " + getAmountAtRisk(instrument));
		console.getErr().println("Used Margin: " + account.getUsedMargin() + " $");
		console.getErr().println("Use of Leverage: " + account.getUseOfLeverage() + " %");
		int numOrders = 0;
		for (IOrder order : engine.getOrders()) {
			if (order.getState() == IOrder.State.FILLED || order.getState() == IOrder.State.OPENED
					|| order.getState() == IOrder.State.CREATED) {
				numOrders++;
			}
		}
		console.getErr().println("Num Open Positions: " + numOrders);
	}

	public static boolean isSMACrossBuyNoFeed(IIndicators indicators, Instrument instrument, Period period,
			OfferSide offerSide) throws JFException {

		double sma10 = indicators.ema(instrument, Period.FOUR_HOURS, offerSide, AppliedPrice.CLOSE, 10, 1);
		double sma20 = indicators.ema(instrument, Period.FOUR_HOURS, offerSide, AppliedPrice.CLOSE, 20, 1);
		double sma50 = indicators.ema(instrument, Period.FOUR_HOURS, offerSide, AppliedPrice.CLOSE, 50, 1);
		double sma100 = indicators.ema(instrument, Period.FOUR_HOURS, offerSide, AppliedPrice.CLOSE, 100, 1);
		double sma200 = indicators.ema(instrument, Period.FOUR_HOURS, offerSide, AppliedPrice.CLOSE, 200, 1);

		return ((sma10 > sma20) && (sma20 > sma50) && (sma50 > sma100) && (sma100 > sma200));
	}

	public static boolean isSMACrossSellNoFeed(IIndicators indicators, Instrument instrument, Period period,
			OfferSide offerSide) throws JFException {

		double sma10 = indicators.ema(instrument, Period.FOUR_HOURS, offerSide, AppliedPrice.CLOSE, 10, 1);
		double sma20 = indicators.ema(instrument, Period.FOUR_HOURS, offerSide, AppliedPrice.CLOSE, 20, 1);
		double sma50 = indicators.ema(instrument, Period.FOUR_HOURS, offerSide, AppliedPrice.CLOSE, 50, 1);
		double sma100 = indicators.ema(instrument, Period.FOUR_HOURS, offerSide, AppliedPrice.CLOSE, 100, 1);
		double sma200 = indicators.ema(instrument, Period.FOUR_HOURS, offerSide, AppliedPrice.CLOSE, 200, 1);

		return ((sma10 < sma20) && (sma20 < sma50) && (sma50 < sma100) && (sma100 < sma200));
	}

	/**
	 * FIB PIVOT POINTS!!!!!!!
	 * 
	 * in the following order: 'Central Point (P)', 'Resistance (R1)', 'Support
	 * (S1)', 'Resistance (R2)', 'Support (S2)', 'Resistance (R3)', 'Support (S3)'
	 * 
	 * 
	 * @return
	 * @throws JFException
	 */
	private double calculateBuyTakeProfitFromFib() throws JFException {
		double[][] fibPivot = indicators.fibPivot2(instrument, Period.FOUR_HOURS/* selectedPeriod */, OfferSide.BID,
				indicatorFilter, 1, history.getLastTick(this.instrument).getTime(), 0);

		// P - R1 - R2 - R3
		double[] resistances = new double[] { fibPivot[0][0], fibPivot[1][0], fibPivot[3][0], fibPivot[5][0] };
		double currentValue = history.getLastTick(this.instrument).getBid();

		int firstGreater = Arrays.binarySearch(resistances, currentValue);
		firstGreater = -firstGreater;
		firstGreater++;
		if (firstGreater > 3) {
			firstGreater = 3;
		}
		double priceValue = resistances[firstGreater];
		return roundDouble(priceValue, 5); // + getPipPrice(5/* this.takeProfitPips */);
	}

	private double calculateBuyStopLossFromFib() throws JFException {

		double[][] fibPivot = indicators.fibPivot2(instrument, Period.FOUR_HOURS/* selectedPeriod */, OfferSide.BID,
				indicatorFilter, 1, history.getLastTick(this.instrument).getTime(), 0);

		// P - S1 - S2 - S3
		Double[] supports = new Double[] { fibPivot[0][0], fibPivot[2][0], fibPivot[4][0], fibPivot[6][0] };
		Double currentValue = history.getLastTick(this.instrument).getBid();

		int firstLower = Arrays.binarySearch(supports, currentValue, new MySort());
		firstLower = -firstLower;
		// firstLower++;
		if (firstLower > 3) {
			firstLower = 3;
		}
		double priceValue = supports[firstLower];

		// 5 pips under Support2 level
		return roundDouble(priceValue, 5) - getPipPrice(5/* this.stopLossPips */);

		// return history.g*etLastTick(this.instrument).getBid() -
		// getPipPrice(this.stopLossPips);
	}

	private double calculateTakeProfitFromFibSell() throws JFException {
		double[][] fibPivot = indicators.fibPivot2(instrument, Period.FOUR_HOURS/* selectedPeriod */, OfferSide.BID,
				indicatorFilter, 1, history.getLastTick(this.instrument).getTime(), 0);
		// Just on Support 2
		// return roundDouble(fibPivot[4][0], 5); // + getPipPrice(5/*
		// this.takeProfitPips */);

		// P - S1 - S2 - S3
		Double[] supports = new Double[] { fibPivot[0][0], fibPivot[2][0], fibPivot[4][0], fibPivot[6][0] };
		Double currentValue = history.getLastTick(this.instrument).getBid();

		int firstLower = Arrays.binarySearch(supports, currentValue, new MySort());
		firstLower = -firstLower;
		// firstLower++;
		if (firstLower > 3) {
			firstLower = 3;
		}
		double priceValue = supports[firstLower];
		return roundDouble(priceValue, 5);
	}

	private double calculateSellStopLossFromFib() throws JFException {
		double[][] fibPivot = indicators.fibPivot2(instrument, Period.FOUR_HOURS/* selectedPeriod */, OfferSide.BID,
				indicatorFilter, 1, history.getLastTick(this.instrument).getTime(), 0);

		double[] resistances = new double[] { fibPivot[0][0], fibPivot[1][0], fibPivot[3][0], fibPivot[5][0] };
		double currentValue = history.getLastTick(this.instrument).getBid();

		int firstGreater = Arrays.binarySearch(resistances, currentValue);
		firstGreater = -firstGreater;
		firstGreater++;
		if (firstGreater > 3) {
			firstGreater = 3;
		}
		double priceValue = resistances[firstGreater];
		return roundDouble(priceValue, 5) - getPipPrice(5);
	}

	private double roundDouble(double num, int dec) {
		return Math.round(num * Math.pow(10, dec)) / Math.pow(10, dec);
	}

	private double getPipPrice(int pips) {
		return pips * this.instrument.getPipValue();
	}

	public static String generateStrategyId(final String className) {
		return className.substring(0, MAX_ID_LENGTH);
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

				// message.getReasons().forEach((reason) -> {
				// console.getWarn().println("Reason:" + reason.toString());
				// });
			}
				break;
			default:
				console.getErr().println(orderLabel + " *" + messageType + "* " + message.getContent());
				break;
			}
		}
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

	public static String getLabel(final Instrument instrument, final String id, final int counter) {
		return id + instrument.name().substring(0, 2) + instrument.name().substring(3, 5)
				+ String.format("%8d", counter).replace(" ", "0");
	}

	public void onTick(Instrument instrument, ITick tick) throws JFException {
	}

	static class MySort implements Comparator<Double> {
		public int compare(Double a, Double b) {
			return b.compareTo(a);
		}
	}

	public double getAmountAtRisk(Instrument instrument) throws JFException {
		double totalValueExposed = 0;

		List<IOrder> orders = engine.getOrders();
		for (IOrder order : orders) {
			if (order.getState() == IOrder.State.FILLED || order.getState() == IOrder.State.OPENED
					|| order.getState() == IOrder.State.CREATED) {
				double stop = order.getStopLossPrice();
				if (stop != 0d) {
					double pipsExposed = order.getOpenPrice() - order.getStopLossPrice();
					pipsExposed *= order.isLong() ? 1d : -1d;
					totalValueExposed += pipsExposed * order.getAmount() * 1e6d;
				}
			}
		}
		// return Pairer.convertValueToAccountCurrency(instrument, totalValueExposed);
		return totalValueExposed;
	}
}
