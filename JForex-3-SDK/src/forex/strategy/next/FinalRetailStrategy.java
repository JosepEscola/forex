package forex.strategy.next;

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

public class FinalRetailStrategy implements IStrategy {

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

	private static final int MAX_DECIMAL_CUURENCY = 5; // 3 x GBPJPY

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
	public OfferSide offerSide = OfferSide.ASK;
	@Configurable("Applied price")
	public AppliedPrice appliedPrice = AppliedPrice.CLOSE;
	@Configurable("DC Time period")
	public int dcTimePeriod = 20;

	/********************************************************
	 * TODO: Default: 30 - 75
	 ********************************************************/
	@Configurable("Stop loss")
	private int stopLossPips = 40;
	@Configurable("Take profit")
	private int takeProfitPips = 80;

	private double lastEquity = 0;

	public void onStart(IContext context) throws JFException {
		this.engine = context.getEngine();
		this.history = context.getHistory();
		this.indicators = context.getIndicators();
		this.lastEquity = history.getEquity();
		this.console = context.getConsole();
		this.account = context.getAccount();
	}

	public void onStop() throws JFException {
		double closePL = 0;
		// close all orders
		for (IOrder order : engine.getOrders()) {
			// IOrder orderToClose = engine.getOrder(order.getLabel());
			order.close();
			closePL += order.getProfitLossInAccountCurrency();
		}
		console.getErr().println("TOTAL Closed Order: " + closePL + "$");
	}

	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (!instrument.equals(this.instrument) || !period.equals(this.selectedPeriod) || dontTradePeriod(askBar)) {
			return;
		}

		final IBar prevBar = history.getBar(instrument, selectedPeriod, offerSide, 2);
		final IBar prevPrevBar = history.getBar(instrument, selectedPeriod, offerSide, 3);
		final IBar prevPrevPrevBar = history.getBar(instrument, selectedPeriod, offerSide, 4);

		// ADX: ADX > 30 && Increasing els ultims 15mins
		double[] adx = indicators.adx(instrument, selectedPeriod, offerSide, 14, indicatorFilter, 3, askBar.getTime(),
				0);

		boolean isADX = (adx[2] >= ADX_THRESHOLD);// || ((adx[2] >= 20) && (adx[0] < adx[1]) && (adx[1] < adx[2]));

		/*********************************************************************************************
		 * TODO: after a longer Parabolic SAR run (the aim of this is to ignore shorter
		 * term price changes and focus on more significant trend changes).
		 **********************************************************************************************/
		double[] sar = indicators.sar(instrument, period, offerSide, 0.02, 0.2, indicatorFilter, 4, askBar.getTime(),
				0);
		boolean isSARBuy = false, isSARSell = false;

		// [0] past - [3] last SAR value
		if ((sar[3] < askBar.getLow()) && (sar[2] > prevBar.getHigh()) || sar[1] > prevPrevBar.getHigh()
		// || sar[0] > prevPrevPrevBar.getHigh()
		) {
			isSARBuy = true;
		}

		double[] shortMA = indicators.ma(instrument, this.selectedPeriod, offerSide, appliedPrice, shortMAPeriod,
				shortMAType, indicatorFilter, 2, askBar.getTime(), 0);
		double[] longMA = indicators.ma(instrument, this.selectedPeriod, offerSide, appliedPrice, longMAPeriod,
				longMAType, indicatorFilter, 2, askBar.getTime(), 0);
		int CURRENT = 1;
		int PREVIOUS = 0;
		boolean isSMABuy = false;
		if (shortMA[PREVIOUS] <= longMA[PREVIOUS] && shortMA[CURRENT] > longMA[CURRENT]) {
			isSMABuy = true;
		}

		if (isSMABuy && isADX && isSARBuy && isRiskManagementOK()) {

			long timeBetweenOrders = (lastOrder == null ? 0 : (askBar.getTime() - lastOrder.getCreationTime()));
			long timeToWait = period.getInterval() * 10;
			if (timeBetweenOrders != 0 && timeBetweenOrders <= timeToWait) {
				console.getErr().println("XXXXXXXXXXX  WAIT MOR TIME!!!!");
				return;
			}
			lastOrder = submitOrder(OrderCommand.BUY, bidBar, askBar);
			return;
		}

		boolean isSMASell = false;
		if (shortMA[PREVIOUS] >= longMA[PREVIOUS] && shortMA[CURRENT] < longMA[CURRENT]) {
			isSMASell = true;
		}

		if ((sar[3] > askBar.getHigh()) && (sar[2] < prevBar.getLow()) || sar[1] < prevPrevBar.getLow()
				|| sar[0] < prevPrevPrevBar.getLow()) {
			isSARSell = true;
		}

		if (isSMASell && isADX && isSARSell && isRiskManagementOK()) {

			long timeBetweenOrders = (lastOrder == null ? 0 : (askBar.getTime() - lastOrder.getCreationTime()));
			long timeToWait = period.getInterval() * 10;
			if (timeBetweenOrders != 0 && timeBetweenOrders <= timeToWait) {
				console.getErr().println("XXXXXXXXXXX WAIT MOR TIME!!!!");
				return;
			}
			lastOrder = submitOrder(OrderCommand.SELL, bidBar, askBar);
		}
	}

	/**
	 * 
	 * @param orderCmd
	 * @return
	 * @throws JFException
	 */
	private IOrder submitOrder(final OrderCommand orderCmd, final IBar bidBar, final IBar askBar) throws JFException {

		double stopLossPrice, takeProfitPrice;

		// Calculating stop loss and take profit prices
		// ITick lastTick = history.getLastTick(this.instrument);

		final Period atrPeriod = Period.FOUR_HOURS;
		if (orderCmd == OrderCommand.BUY) {

			// ATR 21 on 1H/1D chart where TP = 2, 2.5, 3 ATR.
			// 1.5*ATR for SL and 3*ATR for TL. That's 1:2 risk
			final double atr = indicators.atr(instrument, atrPeriod, OfferSide.BID, 21, 1);
			takeProfitPrice = roundDouble((atr * 3) + bidBar.getOpen(), MAX_DECIMAL_CUURENCY);
			stopLossPrice = roundDouble(bidBar.getOpen() - (atr * 1.5), MAX_DECIMAL_CUURENCY);

			/*********************************************
			 * TODO: Posar topes UPPER / LOWER -> Un rango logic fallback --> Mira de posar
			 * un tope d ATR??
			 **********************************************/
			double minTakeProfit = bidBar.getClose() + getPipPrice(this.takeProfitPips);
			double maxTakeProfit = bidBar.getClose() + getPipPrice(this.takeProfitPips * 2);
			if (takeProfitPrice < minTakeProfit) {
				takeProfitPrice = minTakeProfit;
			} else if (takeProfitPrice > maxTakeProfit) {
				takeProfitPrice = maxTakeProfit;
			}
			double minStopLoss = bidBar.getClose() - getPipPrice(this.stopLossPips);
			double maxStopLoss = bidBar.getClose() - getPipPrice(this.stopLossPips * 2);
			if (stopLossPrice > minStopLoss) {
				stopLossPrice = minStopLoss;
			} else if (stopLossPrice < maxStopLoss) {
				stopLossPrice = maxStopLoss;
			}

			// double newamount = calculateAmountFromEquity();
			double newamount = defaultTradeAmount; // Ha donat mes beneficis q el calcul from equity
			final String orderLabel = getLabel(instrument, id, ++counter);

			console.getErr().println("--> BUYING.id: " + orderLabel + " [" + bidBar.getClose() + "]: takeProfitPrice="
					+ takeProfitPrice + " - stopLossPrice:" + stopLossPrice + "\n");
			printOnSubmit();
			return engine.submitOrder(orderLabel, this.instrument, OrderCommand.BUYSTOP, newamount, bidBar.getClose(),
					slippage, stopLossPrice, takeProfitPrice, bidBar.getTime() + Period.WEEKLY.getInterval());

		} else if (orderCmd == OrderCommand.SELL) {

			// ATR 21 on 1H/1D chart where TP = 2, 2.5, 3 ATR.
			// 1.5*ATR for SL and 3*ATR for TL. That's 1:2 risk
			final double atr = indicators.atr(instrument, atrPeriod, OfferSide.BID, 21, 1);
			takeProfitPrice = roundDouble(askBar.getOpen() - (atr * 3), MAX_DECIMAL_CUURENCY);
			stopLossPrice = roundDouble((atr * 1.5) + askBar.getOpen(), MAX_DECIMAL_CUURENCY);

			double minTakeProfit = askBar.getClose() - getPipPrice(this.takeProfitPips);
			double maxTakeProfit = askBar.getClose() - getPipPrice(this.takeProfitPips * 2);
			if (takeProfitPrice > minTakeProfit) {
				takeProfitPrice = minTakeProfit;
			} else if (takeProfitPrice < maxTakeProfit) {
				takeProfitPrice = maxTakeProfit;
			}

			double minStopLoss = askBar.getClose() + getPipPrice(this.stopLossPips);
			double maxStopLoss = askBar.getClose() + getPipPrice(this.stopLossPips * 2);
			if (stopLossPrice < minStopLoss) {
				stopLossPrice = minStopLoss;
			} else if (stopLossPrice > maxStopLoss) {
				stopLossPrice = maxStopLoss;
			}

			// double newamount = calculateAmountFromEquity();
			double newamount = defaultTradeAmount; // Ha donat mes beneficis q el calcul from equity
			final String orderLabel = getLabel(instrument, id, ++counter);

			console.getErr().println("--> SELLING.id: " + orderLabel + " [" + askBar.getClose() + "]: takeProfitPrice="
					+ takeProfitPrice + " - stopLossPrice:" + stopLossPrice + "\n");
			printOnSubmit();

			return engine.submitOrder(orderLabel, this.instrument, OrderCommand.SELLSTOP, newamount, askBar.getClose(),
					slippage, stopLossPrice, takeProfitPrice, askBar.getTime() + Period.WEEKLY.getInterval());

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

	public void onAccount(IAccount account) throws JFException {
	}

	public void onMessage(IMessage message) throws JFException {
		printOnMessage(console, message, id);
	}
}
