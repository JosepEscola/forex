package forex.strategy.next;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
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
import com.dukascopy.api.IIndicators.MaType;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class MacdSmaSarStrategy implements IStrategy {

	private static final int ADX_THRESHOLD = 30;

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
	private Filter indicatorFilter = Filter.ALL_FLATS;
	@Configurable("Amount")
	private double defaultTradeAmount = 0.1;
	@Configurable("slippage:")
	public int slippage = 0;

	@Configurable("Short MA type")
	public MaType shortMAType = MaType.SMA;
	@Configurable("Short MA time period")
	public int shortMAPeriod = 5;
	@Configurable("Long MA type")
	public MaType longMAType = MaType.SMA;
	@Configurable("Long MA time period")
	public int longMAPeriod = 30;
	@Configurable("Offer side")
	public OfferSide offerSide = OfferSide.BID;
	@Configurable("Applied price")
	public AppliedPrice appliedPrice = AppliedPrice.CLOSE;

	// @Configurable("Trade close hour GMT")
	public String endTime = "22:55";

	/********************************************************
	 * TODO: Define STOP _LOSSS: Baixat a 50
	 ********************************************************/
	@Configurable("Stop loss")
	private int stopLossPips = 75;
	@Configurable("Take profit")
	private int takeProfitPips = 50;

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
		if (!instrument.equals(this.instrument) || !period.equals(this.selectedPeriod) || dontTradePeriod(askBar)) {
			return;
		}

		final IBar prevBar = history.getBar(instrument, selectedPeriod, offerSide, 2);
		final IBar prevPrevBar = history.getBar(instrument, selectedPeriod, offerSide, 3);

		// ADX: ADX > 30 && Increasing els ultims 15mins
		double[] adx = indicators.adx(instrument, Period.THIRTY_SECS, offerSide, 14, indicatorFilter, 3,
				askBar.getTime(), 0);

		boolean isADX = adx[2] >= ADX_THRESHOLD; // && adx[0] < adx[1] && adx[1] < adx[2];

		/*********************************************************************************************
		 * TODO: after a longer Parabolic SAR run (the aim of this is to ignore shorter
		 * term price changes and focus on more significant trend changes).
		 **********************************************************************************************/
		// Get 3 candles back: 2 igual per seguretat: evitem un SAR = Low - High - Low
		double[] sar = indicators.sar(instrument, period, offerSide, 0.01, 0.1, indicatorFilter, 3, askBar.getTime(),
				0);
		boolean isSARBuy = (sar[0] > prevPrevBar.getHigh() && sar[1] > prevBar.getHigh() && sar[2] < askBar.getLow());
		boolean isSARSell = (sar[0] < prevPrevBar.getLow() && sar[1] < prevBar.getLow() && sar[2] > askBar.getHigh());

		// open order signals with MA
		double[] shortMA = indicators.ma(instrument, this.selectedPeriod, offerSide, appliedPrice, shortMAPeriod,
				shortMAType, indicatorFilter, 5, askBar.getTime(), 0);
		double[] longMA = indicators.ma(instrument, this.selectedPeriod, offerSide, appliedPrice, longMAPeriod,
				longMAType, indicatorFilter, 5, askBar.getTime(), 0);

		boolean isSMABuy = false;
		// Diferencia entre longMa and shortMA decreasing --> BUY (befoer MA Crossing)
		if ((longMA[0] - shortMA[0]) > (longMA[1] - shortMA[1]) && (longMA[1] - shortMA[1]) > (longMA[2] - shortMA[2])
				&& (longMA[2] - shortMA[2]) > (longMA[3] - shortMA[3])
				&& (longMA[3] - shortMA[3]) > (longMA[4] - shortMA[4])) {
			isSMABuy = true;
		}

		double[] macd0 = indicators.macd(instrument, this.selectedPeriod, offerSide, appliedPrice, 12, 26, 9, 0);
		double[] macd1 = indicators.macd(instrument, this.selectedPeriod, offerSide, appliedPrice, 12, 26, 9, 1);

		final int HIST = 2;
		boolean isMACDBuy = false, isMACDSell = false;
		if (macd0[HIST] > 0 && macd1[HIST] <= 0) {
			isMACDBuy = true;
		}
		if (macd0[HIST] < 0 && macd1[HIST] >= 0) {
			isMACDSell = true;
		}

		if (isSMABuy && isADX && isSARBuy && isMACDBuy && isRiskManagementOK()) {

			long timeBetweenOrders = (lastOrder == null ? 0 : (askBar.getTime() - lastOrder.getCreationTime()));
			long timeToWait = period.getInterval() * 5;
			if (timeBetweenOrders != 0 && timeBetweenOrders <= timeToWait) {
				console.getErr().println("XXXXXXXXXXX  WAIT MOR TIME!!!!");
				return;
			}
			lastOrder = submitOrder(OrderCommand.BUY);
		}

		boolean isSMASell = false;
		// Diferencia entre shortMA and longMa decreasing --> SELL (before MA Crossing)
		if ((shortMA[0] - longMA[0]) > (shortMA[1] - longMA[1]) && (shortMA[1] - longMA[1]) > (shortMA[2] - longMA[2])
				&& (shortMA[2] - longMA[2]) > (shortMA[3] - longMA[3])
				&& (shortMA[3] - longMA[3]) > (shortMA[4] - longMA[4])) {
			isSMASell = true;
		}

		if (isSMASell && isADX && isSARSell && isMACDSell && isRiskManagementOK()) {

			long timeBetweenOrders = (lastOrder == null ? 0 : (bidBar.getTime() - lastOrder.getCreationTime()));
			long timeToWait = period.getInterval() * 5;
			if (timeBetweenOrders != 0 && timeBetweenOrders <= timeToWait) {
				console.getErr().println("XXXXXXXXXXX WAIT MOR TIME!!!!");
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

	private boolean dontTradePeriod(IBar askBar) {
		final Calendar cal = Calendar.getInstance();
		cal.setTime(new Date(askBar.getTime()));

		return (cal.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY && cal.get(Calendar.HOUR_OF_DAY) >= 18)
				|| (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY)
				|| (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY);
	}
}
